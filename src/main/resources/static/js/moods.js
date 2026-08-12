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

            // Single-series trend line: one hue from the app palette (validated for
            // contrast against the white chart surface), thin 2px line, small
            // markers, light fill. No legend - the "Mood Trend" heading names it.
            const ctx = document.getElementById('moodTrendChart').getContext('2d');
            new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'Mood Rating',
                        data: dataPoints,
                        borderColor: '#5b52d6',
                        backgroundColor: 'rgba(91, 82, 214, 0.12)',
                        pointBackgroundColor: '#5b52d6',
                        pointRadius: 4,
                        pointHoverRadius: 6,
                        borderWidth: 2,
                        tension: 0.25,
                        fill: true
                    }]
                },
                options: {
                    plugins: {
                        legend: {
                            display: false
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            max: 10,
                            grid: {
                                color: '#e3ded6'
                            }
                        },
                        x: {
                            grid: {
                                display: false
                            }
                        }
                    }
                }
            });
        });
});