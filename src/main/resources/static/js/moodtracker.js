document.addEventListener("DOMContentLoaded", function () {
    const dateTimeElement = document.getElementById("currentDateTime");
    const currentDateTime = new Date().toISOString();
    dateTimeElement.innerHTML += currentDateTime;

    // Set the hidden input field value
    if (document.getElementById("clientCurrentDateTime")) {
        document.getElementById("clientCurrentDateTime").value = currentDateTime;
    }
});
