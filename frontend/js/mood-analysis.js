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
            color: '#a8c2c9',
        },
        {
            label: 'Rainy',
            moods: withWeather.filter((mood) => mood.weather.precipitationMm > 0),
            color: '#5b7f8c',
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

// Groups moods by local calendar day for the /moods calendar heatmap,
// averaging moodRating when more than one entry falls on the same day (same
// choice the weekly summary stat card already makes). Entries with a missing
// rating or an unparseable date are dropped, matching buildSeries - a day
// where every entry lacks a rating is dropped entirely rather than kept with
// a null average, so the heatmap only ever colors days it has real data for.
function buildHeatmapData(data) {
    const ratingsByDate = new Map();

    data.forEach((mood) => {
        const date = new Date(mood.date);
        if (
            Number.isNaN(date.getTime()) ||
            mood.moodRating === null ||
            mood.moodRating === undefined
        ) {
            return;
        }
        const key = formatDate(date);
        if (!ratingsByDate.has(key)) {
            ratingsByDate.set(key, []);
        }
        ratingsByDate.get(key).push(mood.moodRating);
    });

    return Array.from(ratingsByDate.entries())
        .map(([date, ratings]) => ({
            date,
            averageRating: ratings.reduce((sum, rating) => sum + rating, 0) / ratings.length,
            count: ratings.length,
        }))
        .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
}

// Slices a full list into one page - used for the /moods "All Saved Moods"
// table, which otherwise renders the account's entire history in one
// unbroken list. Charts and the heatmap keep using the full, unpaginated
// data; only the table itself needs paging. page is 0-based and clamped into
// range, so a stale page number (e.g. after a mood is deleted elsewhere and
// the list gets shorter) never produces an empty slice.
function paginate(items, page, pageSize) {
    const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
    const clampedPage = Math.min(Math.max(page, 0), totalPages - 1);
    const start = clampedPage * pageSize;
    return {
        pageItems: items.slice(start, start + pageSize),
        page: clampedPage,
        totalPages,
    };
}

export {
    formatDateTime,
    formatDate,
    buildSeries,
    computeMoodByWeatherBuckets,
    buildHeatmapData,
    paginate,
    MONTH_ABBREVIATIONS,
};
