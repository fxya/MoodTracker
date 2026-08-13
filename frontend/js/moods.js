// Fetch all moods from the API client-side and render the mood table plus
// the mood/temperature/precipitation trend charts. Pure data transforms
// (buildSeries, computeMoodByWeatherBuckets, date formatting) live in
// mood-analysis.js.
import Chart from 'chart.js/auto';
import { formatDateTime, buildSeries, computeMoodByWeatherBuckets } from './mood-analysis.js';

// Chart.js draws to a canvas, so its colors are plain JS values, not CSS -
// they can't pick up the app's `@media (prefers-color-scheme: dark)` tokens
// (or the data-theme override, set by the inline script in <head> from the
// Settings-page preference) on their own. Read the same resolved state once
// here instead, matching frontend/css/app.css's --color-border /
// --color-border-soft dark values.
function resolveIsDark() {
    const theme = document.documentElement.dataset.theme;
    if (theme === 'dark') {
        return true;
    }
    if (theme === 'light') {
        return false;
    }
    return (
        typeof window !== 'undefined' &&
        typeof window.matchMedia === 'function' &&
        window.matchMedia('(prefers-color-scheme: dark)').matches
    );
}
const GRID_COLOR = resolveIsDark() ? '#453f2e' : '#e8d9bc';

document.addEventListener('DOMContentLoaded', function () {
    fetch('/api/moods')
        .then((response) => response.json())
        .then((data) => {
            renderTable(data);
            renderMoodChart(data);
            renderWeatherCharts(data);
            renderCorrelationChart(data);
        });
});

// Chart.js draws to a canvas, which is opaque to screen readers - this
// builds a visually-hidden (sr-only) table alongside each chart from the
// same {labels, values} data already computed for it, so the information
// isn't chart-only.
function buildDataTable(caption, headers, rows) {
    const wrapper = document.createElement('div');
    wrapper.className = 'sr-only';

    const table = document.createElement('table');
    const captionEl = document.createElement('caption');
    captionEl.textContent = caption;
    table.appendChild(captionEl);

    const headRow = document.createElement('tr');
    headers.forEach((header) => {
        const th = document.createElement('th');
        th.textContent = header;
        headRow.appendChild(th);
    });
    const thead = document.createElement('thead');
    thead.appendChild(headRow);
    table.appendChild(thead);

    const tbody = document.createElement('tbody');
    rows.forEach((row) => {
        const tr = document.createElement('tr');
        row.forEach((cell) => {
            const td = document.createElement('td');
            td.textContent = cell;
            tr.appendChild(td);
        });
        tbody.appendChild(tr);
    });
    table.appendChild(tbody);

    wrapper.appendChild(table);
    return wrapper;
}

function renderTable(data) {
    const tableBody = document.querySelector('#allMoodsTable tbody');
    data.forEach((mood) => {
        const row = document.createElement('tr');
        row.className = 'border-b border-border-soft last:border-0';
        const dateCell = document.createElement('td');
        const ratingCell = document.createElement('td');
        const moodCell = document.createElement('td');
        dateCell.className = 'px-4 py-3 text-ink-muted';
        ratingCell.className = 'px-4 py-3 font-semibold text-ink';
        moodCell.className = 'px-4 py-3 text-ink';

        const date = new Date(mood.date);
        dateCell.textContent = Number.isNaN(date.getTime()) ? mood.date : formatDateTime(date);
        ratingCell.textContent =
            mood.moodRating !== null && mood.moodRating !== undefined ? mood.moodRating : 'N/A';
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
    canvas.setAttribute('aria-label', `${label} over time - see table below for data`);
    canvas.insertAdjacentElement(
        'afterend',
        buildDataTable(
            `${label} over time`,
            ['Date', label],
            labels.map((dateLabel, index) => [dateLabel, values[index]]),
        ),
    );
    new Chart(canvas.getContext('2d'), {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: label,
                    data: values,
                    borderColor: color,
                    backgroundColor: fillColor,
                    pointBackgroundColor: color,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    borderWidth: 2,
                    tension: 0.25,
                    fill: true,
                },
            ],
        },
        options: {
            animation: false,
            plugins: {
                legend: {
                    display: false,
                },
            },
            scales: {
                y: {
                    beginAtZero: true,
                    suggestedMax: suggestedMax,
                    grid: {
                        color: GRID_COLOR,
                    },
                },
                x: {
                    grid: {
                        display: false,
                    },
                },
            },
        },
    });
}

function renderMoodChart(data) {
    const { labels, values } = buildSeries(data, (mood) => mood.moodRating);
    renderLineChart('moodTrendChart', labels, values, {
        color: '#6b7f3f',
        fillColor: 'rgba(107, 127, 63, 0.12)',
        label: 'Mood Rating',
        suggestedMax: 10,
    });
}

// Temperature and precipitation are on very different scales from the mood
// rating (and from each other), so each gets its own chart with its own
// y-axis rather than sharing one - see the dataviz "one axis" rule.
function renderWeatherCharts(data) {
    const withWeather = data.filter((mood) => mood.weather);
    const temperature = buildSeries(withWeather, (mood) => mood.weather.temperatureC);
    const precipitation = buildSeries(withWeather, (mood) => mood.weather.precipitationMm);

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
            color: '#b4502e',
            fillColor: 'rgba(180, 80, 46, 0.12)',
            label: 'Temperature (°C)',
        });
    }

    if (hasPrecipitation) {
        renderLineChart('precipitationChart', precipitation.labels, precipitation.values, {
            color: '#5b7f8c',
            fillColor: 'rgba(91, 127, 140, 0.12)',
            label: 'Precipitation (mm)',
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
    caption.textContent = buckets.map((bucket) => `${bucket.label}: n=${bucket.count}`).join(' · ');
    canvas.setAttribute('aria-label', 'Average mood rating by weather - see table below for data');
    canvas.insertAdjacentElement(
        'afterend',
        buildDataTable(
            'Average mood rating by weather',
            ['Weather', 'Average Mood Rating', 'Entries'],
            buckets.map((bucket) => [bucket.label, bucket.average, bucket.count]),
        ),
    );

    new Chart(canvas.getContext('2d'), {
        type: 'bar',
        data: {
            labels: buckets.map((bucket) => bucket.label),
            datasets: [
                {
                    label: 'Average Mood Rating',
                    data: buckets.map((bucket) => bucket.average),
                    backgroundColor: buckets.map((bucket) => bucket.color),
                    borderRadius: 4,
                    maxBarThickness: 96,
                },
            ],
        },
        options: {
            plugins: {
                legend: {
                    display: false,
                },
            },
            scales: {
                y: {
                    beginAtZero: true,
                    max: 10,
                    grid: {
                        color: GRID_COLOR,
                    },
                },
                x: {
                    grid: {
                        display: false,
                    },
                },
            },
        },
    });
}
