// Shared across specs so every test gets its own isolated user - registration
// doesn't auto-authenticate (UserController.registerUser redirects to
// /login), so this always does both steps.
export const TEST_PASSWORD = 'TestPass123!';

let counter = 0;

// Each Playwright worker is a separate Node process with its own independent
// `counter` starting at 0 - two workers calling this in the same millisecond
// on their first call each produce the same Date.now()+counter pair, causing
// a duplicate-username DB error that surfaces as a flaky, unrelated-looking
// test failure. process.pid differs per worker, so including it makes this
// collision-proof.
export function uniqueUsername() {
    counter += 1;
    return `e2e_${process.pid}_${Date.now()}_${counter}`;
}

export async function registerAndLogin(
    page,
    {
        username = uniqueUsername(),
        password = TEST_PASSWORD,
        email = `${username}@example.com`,
    } = {},
) {
    await page.goto('/register');
    await page.fill('#username', username);
    await page.fill('#email', email);
    await page.fill('#password', password);
    await page.click('button:has-text("Register")');
    await page.waitForURL('**/login');

    await page.fill('#username', username);
    await page.fill('#password', password);
    await page.click('button:has-text("Log in")');
    await page.waitForURL('**/moodtracker');

    return { username, password, email };
}

export async function addMood(page, { text, rating, notes = '', tag = '' }) {
    await page.goto('/moodtracker');
    await page.fill('#mood-text', text);
    await page.fill('#mood-rating', String(rating));
    if (tag) {
        await page.selectOption('#mood-tag', tag);
    }
    if (notes) {
        await page.fill('#notes', notes);
    }
    await page.click('button:has-text("Save Mood")');
    await page.waitForURL('**/moodtracker');
}
