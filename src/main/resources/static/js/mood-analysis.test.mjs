import { describe, it, expect } from 'vitest';
import { formatDateTime, formatDate, buildSeries, computeMoodByWeatherBuckets } from './mood-analysis.js';

describe('formatDateTime', () => {
    it('formats a date as dd-MMM-yyyy HH:mm', () => {
        const date = new Date(2026, 0, 15, 9, 5); // Jan 15 2026, 09:05
        expect(formatDateTime(date)).toBe('15-Jan-2026 09:05');
    });

    it('pads single-digit day, hour, and minute', () => {
        const date = new Date(2026, 7, 3, 4, 7); // Aug 3 2026, 04:07
        expect(formatDateTime(date)).toBe('03-Aug-2026 04:07');
    });
});

describe('formatDate', () => {
    it('formats a date as yyyy-MM-dd', () => {
        expect(formatDate(new Date(2026, 11, 25))).toBe('2026-12-25');
    });

    it('pads single-digit month and day', () => {
        expect(formatDate(new Date(2026, 0, 5))).toBe('2026-01-05');
    });
});

describe('buildSeries', () => {
    it('extracts labels and values sorted chronologically', () => {
        const data = [
            { date: '2026-01-03T00:00:00Z', moodRating: 5 },
            { date: '2026-01-01T00:00:00Z', moodRating: 8 },
            { date: '2026-01-02T00:00:00Z', moodRating: 6 }
        ];

        const { labels, values } = buildSeries(data, mood => mood.moodRating);

        expect(labels).toEqual(['2026-01-01', '2026-01-02', '2026-01-03']);
        expect(values).toEqual([8, 6, 5]);
    });

    it('drops entries where the selected value is null or undefined', () => {
        const data = [
            { date: '2026-01-01T00:00:00Z', moodRating: 5 },
            { date: '2026-01-02T00:00:00Z', moodRating: null },
            { date: '2026-01-03T00:00:00Z' }
        ];

        const { labels, values } = buildSeries(data, mood => mood.moodRating);

        expect(labels).toEqual(['2026-01-01']);
        expect(values).toEqual([5]);
    });

    it('drops entries with an unparseable date', () => {
        const data = [
            { date: 'not-a-date', moodRating: 5 },
            { date: '2026-01-01T00:00:00Z', moodRating: 8 }
        ];

        const { values } = buildSeries(data, mood => mood.moodRating);

        expect(values).toEqual([8]);
    });

    it('returns empty arrays for an empty mood list', () => {
        expect(buildSeries([], mood => mood.moodRating)).toEqual({ labels: [], values: [] });
    });
});

describe('computeMoodByWeatherBuckets', () => {
    it('splits moods into Dry and Rainy buckets by precipitation and averages each', () => {
        const data = [
            { moodRating: 8, weather: { precipitationMm: 0 } },
            { moodRating: 6, weather: { precipitationMm: 0 } },
            { moodRating: 3, weather: { precipitationMm: 5 } },
            { moodRating: 5, weather: { precipitationMm: 2 } }
        ];

        const buckets = computeMoodByWeatherBuckets(data);

        expect(buckets).toEqual([
            { label: 'Dry', color: '#7fa8d9', count: 2, average: 7 },
            { label: 'Rainy', color: '#2062a8', count: 2, average: 4 }
        ]);
    });

    it('rounds averages to two decimal places', () => {
        const data = [
            { moodRating: 8, weather: { precipitationMm: 0 } },
            { moodRating: 7, weather: { precipitationMm: 0 } },
            { moodRating: 5, weather: { precipitationMm: 0 } }
        ];

        const buckets = computeMoodByWeatherBuckets(data);

        expect(buckets).toEqual([{ label: 'Dry', color: '#7fa8d9', count: 3, average: 6.67 }]);
    });

    it('omits a bucket entirely when it has no data, rather than returning it empty', () => {
        const data = [
            { moodRating: 8, weather: { precipitationMm: 0 } },
            { moodRating: 6, weather: { precipitationMm: 0 } }
        ];

        const buckets = computeMoodByWeatherBuckets(data);

        expect(buckets).toEqual([{ label: 'Dry', color: '#7fa8d9', count: 2, average: 7 }]);
    });

    it('returns an empty array when no moods have weather data', () => {
        const data = [{ moodRating: 8, weather: null }, { moodRating: 6 }];

        expect(computeMoodByWeatherBuckets(data)).toEqual([]);
    });

    it('excludes moods with a missing precipitation or rating value', () => {
        const data = [
            { moodRating: 8, weather: { precipitationMm: 0 } },
            { moodRating: null, weather: { precipitationMm: 0 } },
            { moodRating: 5, weather: { precipitationMm: null } }
        ];

        const buckets = computeMoodByWeatherBuckets(data);

        expect(buckets).toEqual([{ label: 'Dry', color: '#7fa8d9', count: 1, average: 8 }]);
    });
});
