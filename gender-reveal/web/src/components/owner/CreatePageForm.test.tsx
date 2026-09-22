import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreatePageForm } from './CreatePageForm';

describe('CreatePageForm', () => {
  it('collects the required fields and calls onSubmit with a PageCreatePayload', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<CreatePageForm onSubmit={onSubmit} submitting={false} error={null} />);

    await user.type(screen.getByLabelText('태명'), '뽀튼이');
    await user.click(screen.getByRole('radio', { name: '남아' }));
    await user.type(screen.getByLabelText('공개 예정 일시'), '2026-10-01T09:00');
    await user.click(screen.getByRole('radio', { name: '케이크' }));
    await user.click(screen.getByRole('button', { name: '미리보기' }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    const payload = onSubmit.mock.calls[0][0];
    expect(payload.nickname).toBe('뽀튼이');
    expect(payload.actualGender).toBe('boy');
    expect(payload.theme).toBe('cake');
    expect(payload.bgmEnabled).toBe(false);
    expect(payload.dueDate).toBeNull();
    expect(payload.message).toBeNull();
    expect(payload.slug).toBeUndefined();
    expect(new Date(payload.revealAt).getTime()).toBe(new Date('2026-10-01T09:00').getTime());
  });

  it('includes optional fields when filled in', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<CreatePageForm onSubmit={onSubmit} submitting={false} error={null} />);

    await user.type(screen.getByLabelText('태명'), '뽀튼이');
    await user.click(screen.getByRole('radio', { name: '여아' }));
    await user.type(screen.getByLabelText('공개 예정 일시'), '2026-10-01T09:00');
    await user.type(screen.getByLabelText('출산 예정일 (선택)'), '2026-11-03');
    await user.type(screen.getByLabelText('축하 메시지 (선택)'), '건강하게 만나요');
    await user.click(screen.getByRole('radio', { name: '풍선' }));
    await user.type(screen.getByLabelText('커스텀 주소 (선택)'), 'our-baby');
    await user.click(screen.getByRole('button', { name: '미리보기' }));

    const payload = onSubmit.mock.calls[0][0];
    expect(payload.dueDate).toBe('2026-11-03');
    expect(payload.message).toBe('건강하게 만나요');
    expect(payload.theme).toBe('balloon');
    expect(payload.slug).toBe('our-baby');
  });

  it('refuses to submit without the required fields', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<CreatePageForm onSubmit={onSubmit} submitting={false} error={null} />);

    await user.click(screen.getByRole('button', { name: '미리보기' }));

    expect(screen.getByRole('alert')).toHaveTextContent('태명, 성별, 공개 예정 일시, 테마를 입력해 주세요');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('shows a server-provided error', () => {
    render(<CreatePageForm onSubmit={() => {}} submitting={false} error="이미 사용 중인 주소예요" />);

    expect(screen.getByRole('alert')).toHaveTextContent('이미 사용 중인 주소예요');
  });

  it('disables the submit button while submitting', () => {
    render(<CreatePageForm onSubmit={() => {}} submitting error={null} />);

    expect(screen.getByRole('button', { name: '미리보기' })).toBeDisabled();
  });
});
