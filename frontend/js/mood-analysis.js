// Pure data-transformation helpers behind the /moods charts - no DOM, no Chart.js.
// Keep this file free of document/window/Chart references; moods.js is where the
// DOM- and Chart.js-dependent rendering lives.

const MONTH_ABBREVIATIONS = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
];

// Matches the "dd-MMM-yyyy HH:mm" format already used for mood dates on
// moodtracker.html, so a mood's date reads the same way everywhere in the app.
function formatDateTime(date) {
    const day = String(date.getDate()).padStart(2, '0');
    const month = MONTH_ABBREVIATIONS[date.getMonth()];
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${day}-${month}-${date.getFullYear()} ${hours}:${minutes}`;
}

function formatDate(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

// Extracts a single numeric series (e.g. mood rating, temperature) from the
// mood list via valueSelector, drops entries where that value is missing,
// and sorts chronologically - the shape every trend chart needs.
function buildSeries(data, valueSelector) {
    const points = data
        .map((mood) => ({ date: new Date(mood.date), value: valueSelector(mood) }))
        .filter(
            (point) =>
                point.value !== null &&
                point.value !== undefined &&
                !Number.isNaN(point.date.getTime()),
        );

    points.sort((a, b) => a.date - b.date);

    return {
        labels: points.map((point) => formatDate(point.date)),
        values: points.map((point) => point.value),
    };
}

// Average mood rating on dry vs. rainy days - an ordinal (dry -> rainy) rather
// than categorical comparison, so both bars share one hue at two lightness
// steps instead of unrelated colors, per the dataviz ordinal-ramp guidance.
// Only returns buckets that actually have data; the caller decides what to do
// when fewer than two buckets come back (currently: hide the chart, since a
// single bar isn't a correlation).
function computeMoodByWeatherBuckets(data) {
    const withWeather = data.filter(
        (mood) =>
            mood.weather &&
            mood.weather.precipitationMm !== null &&
            mood.weather.precipitationMm !== undefined &&
            mood.moodRating !== null &&
            mood.moodRating !== undefined,
    );

    const average = (moods) => moods.reduce((sum, mood) => sum + mood.moodRating, 0) / moods.length;

    return [
        {
            label: 'Dry',
            moods: withWeather.filter((mood) => mood.weather.precipitationMm === 0),
            color: '#7fa8d9',
        },
        {
            label: 'Rainy',
            moods: withWeather.filter((mood) => mood.weather.precipitationMm > 0),
            color: '#2062a8',
        },
    ]
        .filter((bucket) => bucket.moods.length > 0)
        .map((bucket) => ({
            label: bucket.label,
            color: bucket.color,
            count: bucket.moods.length,
            average: Number(average(bucket.moods).toFixed(2)),
        }));
}

export {
    formatDateTime,
    formatDate,
    buildSeries,
    computeMoodByWeatherBuckets,
    MONTH_ABBREVIATIONS,
};
