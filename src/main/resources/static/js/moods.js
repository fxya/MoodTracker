// Fetch all moods from the API client-side and render the mood table plus
// the mood/temperature/precipitation trend charts. Pure data transforms
// (buildSeries, computeMoodByWeatherBuckets, date formatting) live in
// mood-analysis.js, loaded before this file.
document.addEventListener('DOMContentLoaded', function () {
    fetch('/api/moods')
        .then(response => response.json())
        .then(data => {
            renderTable(data);
            renderMoodChart(data);
            renderWeatherCharts(data);
            renderCorrelationChart(data);
        });
});

function renderTable(data) {
    const tableBody = document.querySelector('tbody');
    data.forEach(mood => {
        const row = document.createElement('tr');
        const dateCell = document.createElement('td');
        const ratingCell = document.createElement('td');
        const moodCell = document.createElement('td');

        const date = new Date(mood.date);
        dateCell.textContent = Number.isNaN(date.getTime()) ? mood.date : formatDateTime(date);
        ratingCell.textContent = mood.moodRating !== null && mood.moodRating !== undefined ? mood.moodRating : "N/A";
        moodCell.textContent = mood.mood;

        row.appendChild(dateCell);
        row.appendChild(ratingCell);
        row.appendChild(moodCell);
        tableBody.appendChild(row);
    });
}

// Single-series trend line: one hue from the app palette, thin 2px line,
// small markers, light fill. No legend - the section heading names it.
function renderLineChart(canvasId, labels, values, { color, fillColor, label, suggestedMax }) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) {
        return;
    }
    new Chart(canvas.getContext('2d'), {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: label,
                data: values,
                borderColor: color,
                backgroundColor: fillColor,
                pointBackgroundColor: color,
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
                    suggestedMax: suggestedMax,
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
}

function renderMoodChart(data) {
    const { labels, values } = buildSeries(data, mood => mood.moodRating);
    renderLineChart('moodTrendChart', labels, values, {
        color: '#5b52d6',
        fillColor: 'rgba(91, 82, 214, 0.12)',
        label: 'Mood Rating',
        suggestedMax: 10
    });
}

// Temperature and precipitation are on very different scales from the mood
// rating (and from each other), so each gets its own chart with its own
// y-axis rather than sharing one - see the dataviz "one axis" rule.
function renderWeatherCharts(data) {
    const withWeather = data.filter(mood => mood.weather);
    const temperature = buildSeries(withWeather, mood => mood.weather.temperatureC);
    const precipitation = buildSeries(withWeather, mood => mood.weather.precipitationMm);

    const temperatureSection = document.getElementById('temperatureSection');
    const precipitationSection = document.getElementById('precipitationSection');
    const weatherEmptyState = document.getElementById('weatherEmptyState');

    const hasTemperature = temperature.values.length > 0;
    const hasPrecipitation = precipitation.values.length > 0;

    weatherEmptyState.style.display = hasTemperature || hasPrecipitation ? 'none' : '';
    temperatureSection.style.display = hasTemperature ? '' : 'none';
    precipitationSection.style.display = hasPrecipitation ? '' : 'none';

    if (hasTemperature) {
        renderLineChart('temperatureChart', temperature.labels, temperature.values, {
            color: '#b3590a',
            fillColor: 'rgba(179, 89, 10, 0.12)',
            label: 'Temperature (°C)'
        });
    }

    if (hasPrecipitation) {
        renderLineChart('precipitationChart', precipitation.labels, precipitation.values, {
            color: '#2062a8',
            fillColor: 'rgba(32, 98, 168, 0.12)',
            label: 'Precipitation (mm)'
        });
    }
}

function renderCorrelationChart(data) {
    const section = document.getElementById('correlationSection');
    const canvas = document.getElementById('correlationChart');
    const caption = document.getElementById('correlationCaption');
    if (!section || !canvas || !caption) {
        return;
    }

    const buckets = computeMoodByWeatherBuckets(data);

    // Need at least one day in each bucket for the comparison to mean anything -
    // a single bar isn't a correlation.
    if (buckets.length < 2) {
        section.style.display = 'none';
        return;
    }

    section.style.display = '';
    caption.textContent = buckets.map(bucket => `${bucket.label}: n=${bucket.count}`).join(' · ');

    new Chart(canvas.getContext('2d'), {
        type: 'bar',
        data: {
            labels: buckets.map(bucket => bucket.label),
            datasets: [{
                label: 'Average Mood Rating',
                data: buckets.map(bucket => bucket.average),
                backgroundColor: buckets.map(bucket => bucket.color),
                borderRadius: 4,
                maxBarThickness: 96
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
}
