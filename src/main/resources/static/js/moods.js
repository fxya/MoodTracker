// Fetch all moods from the API client-side
document.addEventListener('DOMContentLoaded', function() {
    fetch('/api/moods')
        .then(response => response.json())
        .then(data => {
            const tableBody = document.querySelector('tbody');
            data.forEach(mood => {
                const row = document.createElement('tr');
                const dateCell = document.createElement('td');
                const ratingCell = document.createElement('td');
                const moodCell = document.createElement('td');

                dateCell.textContent = mood.date;
                ratingCell.textContent = mood.moodRating !== null && mood.moodRating !== undefined ? mood.moodRating : "N/A";
                moodCell.textContent = mood.mood;

                row.appendChild(dateCell);
                row.appendChild(ratingCell);
                row.appendChild(moodCell);
                tableBody.appendChild(row);
            });

            // Chart.js rendering logic
            const validMoods = data.filter(mood => mood.moodRating !== null && mood.moodRating !== undefined);

            validMoods.sort((a, b) => new Date(a.date) - new Date(b.date));

            const labels = validMoods.map(mood => {
                const date = new Date(mood.date);
                return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
            });
            const dataPoints = validMoods.map(mood => mood.moodRating);

            const ctx = document.getElementById('moodTrendChart').getContext('2d');
            new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'Mood Rating Trend',
                        data: dataPoints,
                        borderColor: 'rgb(75, 192, 192)',
                        tension: 0.1
                    }]
                },
                options: {
                    scales: {
                        y: {
                            beginAtZero: true,
                            max: 10
                        }
                    }
                }
            });
        });
});