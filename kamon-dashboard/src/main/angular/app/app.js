angular.module('kamonDashboard', [
  'ngRoute'

]).config(['$routeProvider', function($routeProvider) {
  $routeProvider.when('/metrics', {
    templateUrl: 'metrics/metrics.html',
    controller: 'MetricsController'
  }).when('/actors/:hash', {
    templateUrl: 'actors/actor.html',
    controller: 'ActorController'
  })
  .otherwise({redirectTo: '/metrics'});

}]);