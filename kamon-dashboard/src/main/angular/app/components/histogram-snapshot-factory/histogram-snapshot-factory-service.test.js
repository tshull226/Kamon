describe('a Histogram Snapshot', function() {

  beforeEach(module('kamonDashboard'));

  beforeEach(function() {
    jasmine.addCustomEqualityTester(_histogramEquality);
  })


  it('should establish nrOfMeasurements based on the records passed to it', inject(function(HistogramSnapshotFactory) {
    var snapshot = HistogramSnapshotFactory.create(_createRandomRecords());
    expect(snapshot.nrOfMeasurements).toBeGreaterThan(0);
  }));


  it('should allow associative add non-empty snapshots', inject(function(HistogramSnapshotFactory) {
    _(1000).times(function() {
      var leftSnapshot = HistogramSnapshotFactory.create(_createRandomRecords());
      var rightSnapshot = HistogramSnapshotFactory.create(_createRandomRecords());

      expect(leftSnapshot.add(rightSnapshot)).toEqual(rightSnapshot.add(leftSnapshot));
    })
  }));


  it('should allow associative add of empty and non-empty snapshots', inject(function(HistogramSnapshotFactory) {
    _(1000).times(function() {
      var emptySnapshot = HistogramSnapshotFactory.empty();
      var nonEmptySnapshot = HistogramSnapshotFactory.create(_createRandomRecords());

      expect(emptySnapshot.add(nonEmptySnapshot)).toEqual(nonEmptySnapshot.add(emptySnapshot));
    })
  }));


  it('should allow to substract one snapshot from another', inject(function(HistogramSnapshotFactory) {
    _(1000).times(function() {
      var leftSnapshot = HistogramSnapshotFactory.create(_createRandomRecords());
      var diffSnapshot = HistogramSnapshotFactory.create(_createRandomRecords());
      var rightSnapshot = leftSnapshot.add(diffSnapshot);

      expect(rightSnapshot.substract(leftSnapshot)).toEqual(diffSnapshot);
    });
  }));

  function _createRandomRecords() {
    var recordCount = _.random(1, 100);
    var lastLevel = 1;
    return _.chain(recordCount)
      .times(function(r) {
        lastLevel = _.random(lastLevel + 1, lastLevel + 10);
        return { 
          level: lastLevel,
          count: _.random(1, 10)
        };
      }).sortBy(function(n) {
        return n.level;
      }).value();

  }

  function _histogramEquality(left, right) {

    if(!_(left).has('nrOfMeasurements') ||
       !_(right).has('nrOfMeasurements') ||
       !_(left).has('records') ||
       !_(right).has('records') ||
       left.nrOfMeasurements != right.nrOfMeasurements || 
       left.records.length != right.records.length) {

      return false;
    }

    for(i = 0; i < left.records.length; i++) {
      if(left.records[i].level != right.records[i].level ||
         left.records[i].count != right.records[i].count) {
        return false;
      }
    }

    return true;
  }
  
});