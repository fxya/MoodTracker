document.addEventListener("DOMContentLoaded", function () {
    const dateTimeElement = document.getElementById("currentDateTime");
    const hiddenInputField = document.getElementById("clientCurrentDateTime");

    function updateTime() {
        const currentDateTime = new Date().toISOString();
        dateTimeElement.innerHTML = 'The current date and time is: ' + currentDateTime;

        // Set the hidden input field value
        if (hiddenInputField) {
            hiddenInputField.value = currentDateTime;
        }
    }

    // Update the time immediately upon page load
    updateTime();
    setInterval(updateTime, 1000);
});

