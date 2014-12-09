angular.module('kamonDashboard')
  .controller('ActorController', ['$scope', '$routeParams', 'MetricRepository', function($scope, $routeParams, metricRepository) {
    var actorHash = $routeParams['hash'];
    $scope.actorHash = actorHash;

    var actorMetrics = metricRepository.entityMetrics(actorHash);
    var inputData = actorMetrics === undefined ? [] : actorMetrics.snapshots;
    var xf = crossfilter(inputData);

    // Time dimension
    var timeline = xf.dimension(function(record) { return record.from; });

    // Processed counts group
    var processedCounts = timeline.group().reduceSum(function extractCounts(record) {
      return record.metrics['processing-time'].nrOfMeasurements; 
    });



    $scope.timeline = timeline;
    $scope.processedCounts = processedCounts;



    var composed = dc.lineChart("#chart-counts");
    composed
      .height(300)
      .width(900)
      .dimension(timeline)
      .x(d3.scale.linear().domain([1418098926750, 1418099926750]))
      .xUnits(dc.units.fp.precision(1))
      .group(processedCounts);


    dc.renderAll();

    // Include new snapshots for this actor as they arrive from the server.
    var newSnapshotsSubscription = metricRepository.addEntityMetricsListener(actorHash, function(snapshot) {
      xf.add([snapshot]);
      $scope.$apply();      
      dc.redrawAll();
    });

    $scope.$on('$destroy', function() {
      newSnapshotsSubscription.cancel();
    });


  }]);