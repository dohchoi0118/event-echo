import { ApiError, getGuestbook, getPage, postGuestbook, submitGuess } from './api';

function respond(status: number, body?: unknown) {
  return Promise.resolve(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
}

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('getPage', () => {
  it('parses an open page', async () => {
    fetchMock.mockReturnValue(
      respond(200, {
        status: 'open', nickname: '뽀튼이', actualGender: 'boy', dueDate: '2026-11-03',
        message: '환영해요', theme: 'cake', bgmEnabled: false,
      }),
    );

    const view = await getPage('my-slug');

    expect(view).toEqual({
      status: 'open', nickname: '뽀튼이', actualGender: 'boy', dueDate: '2026-11-03',
      message: '환영해요', theme: 'cake',
    });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/pages/my-slug');
    expect(init).toMatchObject({ cache: 'no-store', credentials: 'same-origin' });
  });

  it('drops the gender-bearing fields of secret and expired pages', async () => {
    fetchMock.mockReturnValueOnce(respond(200, { status: 'secret', nickname: '뽀튼이', actualGender: null }));
    fetchMock.mockReturnValueOnce(respond(200, { status: 'expired', nickname: '뽀튼이' }));

    expect(await getPage('a')).toEqual({ status: 'secret', nickname: '뽀튼이' });
    expect(await getPage('b')).toEqual({ status: 'expired', nickname: '뽀튼이' });
  });

  it('encodes the slug', async () => {
    fetchMock.mockReturnValue(respond(200, { status: 'secret', nickname: 'x' }));

    await getPage('a b/c');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/pages/a%20b%2Fc');
  });

  it('throws ApiError with status and body on failure', async () => {
    fetchMock.mockReturnValue(respond(404, { error: 'Page not found: nope' }));

    await expect(getPage('nope')).rejects.toMatchObject({ status: 404, body: { error: 'Page not found: nope' } });
    await expect(getPage('nope')).rejects.toBeInstanceOf(ApiError);
  });

  it('rejects an unknown status value', async () => {
    fetchMock.mockReturnValue(respond(200, { status: 'weird', nickname: 'x' }));

    await expect(getPage('x')).rejects.toThrow();
  });
});

describe('submitGuess', () => {
  it('posts the guess as JSON and reports a fresh guess', async () => {
    fetchMock.mockReturnValue(respond(201, { guessedGender: 'girl', createdAt: '2026-09-21T00:00:00.000Z' }));

    const result = await submitGuess('s', 'girl');

    expect(result).toEqual({ guessedGender: 'girl', alreadyGuessed: false });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/pages/s/guesses');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ guessedGender: 'girl' });
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json' });
  });

  it('turns a duplicate-guess 409 into the earlier guess', async () => {
    fetchMock.mockReturnValue(respond(409, { error: 'dup', guessedGender: 'boy' }));

    expect(await submitGuess('s', 'girl')).toEqual({ guessedGender: 'boy', alreadyGuessed: true });
  });

  it('rethrows a 409 that carries no earlier guess (page not open)', async () => {
    fetchMock.mockReturnValue(respond(409, { error: 'Page is not open' }));

    await expect(submitGuess('s', 'girl')).rejects.toMatchObject({ status: 409 });
  });
});

describe('guestbook calls', () => {
  it('lists entries', async () => {
    const entries = [{ nickname: '이모', message: '축하해요', createdAt: '2026-09-21T00:00:00.000Z' }];
    fetchMock.mockReturnValue(respond(200, entries));

    expect(await getGuestbook('s')).toEqual(entries);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/pages/s/guestbook');
  });

  it('posts a new entry', async () => {
    const created = { nickname: '이모', message: '축하해요', createdAt: '2026-09-21T00:00:00.000Z' };
    fetchMock.mockReturnValue(respond(201, created));

    expect(await postGuestbook('s', { nickname: '이모', message: '축하해요' })).toEqual(created);
    const init = fetchMock.mock.calls[0][1];
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ nickname: '이모', message: '축하해요' });
  });

  it('surfaces validation failures as ApiError 400', async () => {
    fetchMock.mockReturnValue(respond(400, { errors: ['nickname'] }));

    await expect(postGuestbook('s', { nickname: '', message: '' })).rejects.toMatchObject({ status: 400 });
  });
});
