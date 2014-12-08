angular.module('kamonDashboard')
  .controller('MetricsController', ['$scope', 'MetricRepository', 'HistogramSnapshot', function($scope, mr, hs) {
    var histogram = hs.create([]);
    $scope.test = "test string " + histogram.nrOfMeasurements;

}]);