angular.module('kamonDashboard')
  .factory('CrossfilterTools', ['HistogramSnapshotFactory', function(histogramSnapshotFactory) {

    function _sanitizeEntitySnapshot(entitySnapshot) {
      entitySnapshot.from = new Date(entitySnapshot.from);
      entitySnapshot.to = new Date(entitySnapshot.to);
      return entitySnapshot;
    }

    // This expects to receive a crossfilter that contains entity snapshots only.
    function _createMetricGroup(crossfilter, metricName, metricType) {
      if(metricType == 'histogram') {
        var addMetric = function(p, h) { return p.add(h.metrics[metricName]); };
        var substractMetric = function(p, h) { return p.substract(h.metrics[metricName]); };
        var initialValue = function() { return histogramSnapshotFactory.create([]); };

        return crossfilter.groupAll().reduce(addMetric, substractMetric, initialValue);

      } else if(metricType == 'counter') {
        
      }
    }

    return {
      sanitizeEntitySnapshot: _sanitizeEntitySnapshot,
      createMetricGroup: _createMetricGroup
    };

  }]);