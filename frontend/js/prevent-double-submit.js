// None of the app's forms are JS-driven (plain Thymeleaf POSTs), so nothing
// stops a double-click/double-tap from firing two identical submissions -
// most visibly, two identical mood rows. Disabling the button that triggered
// submission still lets that submission go through; it just blocks a second
// one before the page navigates away.
document.addEventListener('submit', (event) => {
    const submitter =
        event.submitter ||
        event.target.querySelector('button[type="submit"], input[type="submit"]');
    if (submitter) {
        submitter.disabled = true;
    }
});
