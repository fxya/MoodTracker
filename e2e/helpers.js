// Shared across specs so every test gets its own isolated user - registration
// doesn't auto-authenticate (UserController.registerUser redirects to
// /login), so this always does both steps.
export const TEST_PASSWORD = 'TestPass123!';

let counter = 0;

export function uniqueUsername() {
    counter += 1;
    return `e2e_${Date.now()}_${counter}`;
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

export async function addMood(page, { text, rating, notes = '' }) {
    await page.goto('/moodtracker');
    await page.fill('#mood-text', text);
    await page.fill('#mood-rating', String(rating));
    if (notes) {
        await page.fill('#notes', notes);
    }
    await page.click('button:has-text("Save Mood")');
    await page.waitForURL('**/moodtracker');
}
