angular.module('kamonDashboard')
  .factory('MetricRepository', ['EventStream', '$log', function(eventStream, $log) {
    $log.info('Creating the metrics MetricRepository');


    return function() {

    };
  }]);