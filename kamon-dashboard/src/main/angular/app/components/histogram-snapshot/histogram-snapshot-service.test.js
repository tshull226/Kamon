describe('a Histogram Snapshot', function() {

  beforeEach(module('kamonDashboard'));

  it('should establish nrOfMeasurements based on the stored records', inject(function(HistogramSnapshot) {
    var h = HistogramSnapshot.create([
      { level: 5, count: 3 },
      { level: 12, count: 4 },
      { level: 24, count: 10 }
    ]);

    expect(h.nrOfMeasurements).toBe(17);
  }));


  it('should allow two snapshots to be added together', inject(function(HistogramSnapshot) {
    var h1 = HistogramSnapshot.create([
      { level: 1, count: 3 },
      { level: 3, count: 4 },
      { level: 5, count: 10 }
    ]);

    var h2 = HistogramSnapshot.create([
      { level: 2, count: 3 },
      { level: 3, count: 4 },
      { level: 4, count: 11 }
    ]);

    h1.add(h2);
    expect(h1.nrOfMeasurements).toBe(35);
    expect(h1.records).toEqual([
      { level: 1, count: 3 },
      { level: 2, count: 3 },
      { level: 3, count: 8 },
      { level: 4, count: 11 },
      { level: 5, count: 10 }
    ]);
  }));

  it('should allow adding empty and non-empty snapshots', inject(function(HistogramSnapshot) {
    var h1 = HistogramSnapshot.create([]);

    var h2 = HistogramSnapshot.create([
      { level: 2, count: 3 },
      { level: 3, count: 4 },
      { level: 4, count: 11 }
    ]);

    h1.add(h2);
    expect(h1.nrOfMeasurements).toBe(18);
    expect(h1.records).toEqual([
      { level: 2, count: 3 },
      { level: 3, count: 4 },
      { level: 4, count: 11 }
    ]);
  }));

  it('should allow to substract one snapshot from another', inject(function(HistogramSnapshot) {
    var h1 = HistogramSnapshot.create([
      { level: 1, count: 3 },
      { level: 3, count: 4 },
      { level: 4, count: 4 },
      { level: 5, count: 10 }
    ]);

    var h2 = HistogramSnapshot.create([
      { level: 1, count: 2 },
      { level: 3, count: 6 }
    ]);

    h1.substract(h2);
    expect(h1.nrOfMeasurements).toBe(15);
    expect(h1.records).toEqual([
      { level: 1, count: 1 },
      { level: 3, count: 0 },
      { level: 4, count: 4 },
      { level: 5, count: 10 }
    ]);
  }));
  
});