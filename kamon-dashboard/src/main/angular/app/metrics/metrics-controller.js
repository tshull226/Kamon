angular.module('kamonDashboard')
  .controller('MetricsController', ['$scope', 'MetricRepository', 'HistogramSnapshotFactory', '$log', function($scope, metricRepository, histogramSnapshot, $log) {

    $scope.test = "test string ";
    $scope.entities = metricRepository.allEntities();


    // This couple things are necessary to ensure that the $scope is correctly updated when the server pushes data.
    metricRepository.addMetricBatchArriveListener(function() { $scope.$apply(); });
    $scope.$watch(
      function() { return metricRepository.allEntities(); },
      function(newVal) { $scope.entities = newVal; }
    );

}]);