import {
  ApiError, createPage, deleteGuestbookEntry, extendPage, getGuestbook, getMe, getOwnerGuestbook,
  getOwnerPageDetail, getOwnerStats, getPage, listOwnerPages, logout, postGuestbook, requestMagicLink,
  setGuestbookHidden, submitGuess,
} from './api';

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

describe('owner auth calls', () => {
  it('requests a magic link', async () => {
    fetchMock.mockReturnValue(respond(202));

    await requestMagicLink('owner@example.com');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/magic-link');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ email: 'owner@example.com' });
  });

  it('gets the current owner', async () => {
    fetchMock.mockReturnValue(respond(200, { email: 'owner@example.com' }));

    expect(await getMe()).toEqual({ email: 'owner@example.com' });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/me');
  });

  it('propagates 401 from getMe as ApiError', async () => {
    fetchMock.mockReturnValue(respond(401, { error: 'Unauthorized' }));

    await expect(getMe()).rejects.toMatchObject({ status: 401 });
  });

  it('logs out', async () => {
    fetchMock.mockReturnValue(respond(204));

    await logout();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/logout');
    expect(init.method).toBe('POST');
  });
});

describe('owner page calls', () => {
  it('lists owner pages', async () => {
    const pages = [{ slug: 's', nickname: '뽀', status: 'open', revealAt: 'x', expiresAt: 'y', extended: false, theme: 'box' }];
    fetchMock.mockReturnValue(respond(200, pages));

    expect(await listOwnerPages()).toEqual(pages);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/owner/pages');
  });

  it('gets owner page detail', async () => {
    const detail = { slug: 's', nickname: '뽀', actualGender: 'boy', dueDate: null, message: null, theme: 'box', bgmEnabled: false, status: 'open', revealAt: 'x', createdAt: 'y', expiresAt: 'z', extended: false };
    fetchMock.mockReturnValue(respond(200, detail));

    expect(await getOwnerPageDetail('s')).toEqual(detail);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/owner/pages/s');
  });

  it('gets owner stats', async () => {
    const stats = { visitors: 1, guessers: 1, boyGuesses: 1, girlGuesses: 0 };
    fetchMock.mockReturnValue(respond(200, stats));

    expect(await getOwnerStats('s')).toEqual(stats);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/owner/pages/s/stats');
  });

  it('extends a page', async () => {
    fetchMock.mockReturnValue(respond(200, { slug: 's', nickname: '뽀', status: 'open', revealAt: 'x', expiresAt: 'y', extended: true, theme: 'box' }));

    const result = await extendPage('s');

    expect(result.extended).toBe(true);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/owner/pages/s/extend');
    expect(init.method).toBe('POST');
  });

  it('creates a page', async () => {
    fetchMock.mockReturnValue(respond(201, { slug: 'new-slug' }));

    const payload = {
      nickname: '뽀튼이', actualGender: 'boy' as const, revealAt: '2026-10-01T00:00:00.000Z',
      dueDate: null, message: null, theme: 'box' as const, bgmEnabled: false, slug: undefined,
    };
    const result = await createPage(payload);

    expect(result).toEqual({ slug: 'new-slug' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/pages');
    expect(JSON.parse(init.body)).toEqual(payload);
  });
});

describe('owner guestbook moderation calls', () => {
  it('lists owner guestbook entries', async () => {
    const entries = [{ id: 1, nickname: '이모', message: '축하', createdAt: 'x', hidden: false }];
    fetchMock.mockReturnValue(respond(200, entries));

    expect(await getOwnerGuestbook('s')).toEqual(entries);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/owner/pages/s/guestbook');
  });

  it('sets an entry hidden', async () => {
    fetchMock.mockReturnValue(respond(204));

    await setGuestbookHidden('s', 1, true);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/owner/pages/s/guestbook/1');
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(init.body)).toEqual({ hidden: true });
  });

  it('deletes an entry', async () => {
    fetchMock.mockReturnValue(respond(204));

    await deleteGuestbookEntry('s', 1);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/owner/pages/s/guestbook/1');
    expect(init.method).toBe('DELETE');
  });
});
