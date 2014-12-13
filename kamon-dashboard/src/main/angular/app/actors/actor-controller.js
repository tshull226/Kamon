angular.module('kamonDashboard')
  .controller('ActorController', ['$scope', '$routeParams', 'MetricRepository', 'HistogramSnapshotFactory', 'CrossfilterTools', function($scope, $routeParams, metricRepository, histogramSnapshotFactory, xfTools) {
    var actorHash = $routeParams.hash;
    var actorMetrics = metricRepository.entityMetrics(actorHash);
    var xfData = (actorMetrics === undefined ? [] : actorMetrics.snapshots).map(xfTools.sanitizeEntitySnapshot);

    var xf = crossfilter(xfData);
    var timeline = xf.dimension(function(record) { return record.from; });
    var processedCountByTime = timeline.group().reduceSum(function extractCounts(record) {
      return record.metrics['processing-time'].nrOfMeasurements; 
    });



    var processingTimeDistribution = xfTools.createMetricGroup(xf, 'processing-time', 'histogram');
    var fakeDistributionGroup = {
      all: function() {
        return processingTimeDistribution.value().records.map(function(r) {
          return { key: r.level, value: r.count };
        });
      }
    };

    $scope.fullSpectrum = processingTimeDistribution;


    var _ticker = 0;
    $scope.ticker = function() { return _ticker; };
    $scope.timeline = timeline;
    $scope.processedCountByTime = processedCountByTime;
    $scope.countsByProcessingTime = fakeDistributionGroup;



    // Include new snapshots for this actor as they arrive from the server.
    var newSnapshotsSubscription = metricRepository.addEntityMetricsListener(actorHash, function(snapshot) {
      $scope.$apply(function() {
        _ticker = snapshot.to;
        xf.add([xfTools.sanitizeEntitySnapshot(snapshot)]);
      });
    });

    var newMetricBatchSubscription = metricRepository.addMetricBatchArriveListener(function(newBatchInterval) {
      $scope.$apply(function() {
        _ticker = newBatchInterval.to;
      });
    });

    $scope.$on('$destroy', function() {
      newSnapshotsSubscription.cancel();
      newMetricBatchSubscription.cancel();
    });


  }]);