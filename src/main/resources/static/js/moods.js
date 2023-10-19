// Fetch all moods from the API client-side
document.addEventListener('DOMContentLoaded', function() {
    fetch('/api/moods')
        .then(response => response.json())
        .then(data => {
            const tableBody = document.querySelector('tbody');
            data.forEach(mood => {
                const row = document.createElement('tr');
                const dateCell = document.createElement('td');
                const emptyCell = document.createElement('td');
                const moodCell = document.createElement('td');

                dateCell.textContent = mood.date;
                moodCell.textContent = mood.mood;

                row.appendChild(dateCell);
                row.appendChild(emptyCell);
                row.appendChild(moodCell);
                tableBody.appendChild(row);
            });
        });
});