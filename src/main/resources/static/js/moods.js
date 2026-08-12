// Fetch all moods from the API client-side and render the mood table plus
// the mood/temperature/precipitation trend charts.
document.addEventListener('DOMContentLoaded', function () {
    fetch('/api/moods')
        .then(response => response.json())
        .then(data => {
            renderTable(data);
            renderMoodChart(data);
            renderWeatherCharts(data);
        });
});

function renderTable(data) {
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
}

// Extracts a single numeric series (e.g. mood rating, temperature) from the
// mood list via valueSelector, drops entries where that value is missing,
// and sorts chronologically - the shape every trend chart here needs.
function buildSeries(data, valueSelector) {
    const points = data
        .map(mood => ({ date: new Date(mood.date), value: valueSelector(mood) }))
        .filter(point => point.value !== null && point.value !== undefined && !Number.isNaN(point.date.getTime()));

    points.sort((a, b) => a.date - b.date);

    return {
        labels: points.map(point => formatDate(point.date)),
        values: points.map(point => point.value)
    };
}

function formatDate(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
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
