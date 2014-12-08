angular.module('kamonDashboard', [
  'ngRoute'

]).config(['$routeProvider', function($routeProvider) {
  $routeProvider.when('/metrics', {
    templateUrl: 'metrics/metrics.html',
    controller: 'MetricsController'
  })
  .otherwise({redirectTo: '/metrics'});

}]);