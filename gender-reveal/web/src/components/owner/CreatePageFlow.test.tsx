import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreatePageFlow } from './CreatePageFlow';
import * as api from '@/lib/api';

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>();
  return { ...actual, createPage: vi.fn() };
});

const createPage = vi.mocked(api.createPage);

async function fillMinimalForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('태명'), '뽀튼이');
  await user.click(screen.getByRole('radio', { name: '남아' }));
  await user.type(screen.getByLabelText('공개 예정 일시'), '2026-10-01T09:00');
  await user.click(screen.getByRole('radio', { name: '서프라이즈 박스' }));
}

beforeEach(() => {
  createPage.mockReset();
});

describe('CreatePageFlow', () => {
  it('walks form → preview → publish → success', async () => {
    createPage.mockResolvedValue({ slug: 'ppo-2026' });
    const user = userEvent.setup();
    render(<CreatePageFlow />);

    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: '미리보기' }));

    expect(screen.getByText('이렇게 만들어져요')).toBeInTheDocument();
    expect(screen.getByText('뽀튼이')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '발행하기' }));

    expect(createPage).toHaveBeenCalledTimes(1);
    expect(await screen.findByText('페이지가 발행됐어요!')).toBeInTheDocument();
    expect(screen.getByText('이메일로도 링크를 보내드렸어요')).toBeInTheDocument();
    expect(screen.getByText(/ppo-2026/)).toBeInTheDocument();
  });

  it('lets the owner go back from preview to edit the form', async () => {
    const user = userEvent.setup();
    render(<CreatePageFlow />);

    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: '미리보기' }));
    await user.click(screen.getByRole('button', { name: '← 다시 입력하기' }));

    expect(screen.getByLabelText('태명')).toHaveValue('뽀튼이');
    expect(createPage).not.toHaveBeenCalled();
  });

  it('shows a slug-taken error on the form after a 409', async () => {
    createPage.mockRejectedValue(new api.ApiError(409, { error: 'Slug already taken' }));
    const user = userEvent.setup();
    render(<CreatePageFlow />);

    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: '미리보기' }));
    await user.click(screen.getByRole('button', { name: '발행하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 사용 중인 주소예요');
    expect(screen.getByLabelText('태명')).toBeInTheDocument();
  });

  it('shows a validation error on the form after a 400', async () => {
    createPage.mockRejectedValue(new api.ApiError(400, { error: 'Invalid revealAt' }));
    const user = userEvent.setup();
    render(<CreatePageFlow />);

    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: '미리보기' }));
    await user.click(screen.getByRole('button', { name: '발행하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('입력값을 다시 확인해 주세요');
    expect(screen.getByLabelText('태명')).toBeInTheDocument();
  });

  it('copies the link on the success screen', async () => {
    createPage.mockResolvedValue({ slug: 'ppo-2026' });
    const user = userEvent.setup();
    // userEvent.setup() installs its own navigator.clipboard stub (for copy/paste emulation) and
    // overwrites any mock assigned before this call, so the writeText spy must be attached after it.
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);
    render(<CreatePageFlow />);

    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: '미리보기' }));
    await user.click(screen.getByRole('button', { name: '발행하기' }));
    await user.click(await screen.findByRole('button', { name: '링크 복사' }));

    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('/g/ppo-2026'));
    expect(await screen.findByText('복사했어요')).toBeInTheDocument();
  });
});
