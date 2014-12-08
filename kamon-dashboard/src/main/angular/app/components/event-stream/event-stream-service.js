angular.module('kamonDashboard')
  .factory('EventStream', ['$log', function($log) {

    var sse = new EventSource("http://127.0.0.1:9099/event-stream");
    var subscribers = {};


    var subscribe = function(topics, callback) {

    };

    sse.onmessage = function(event) {
      var m = JSON.parse(event.data);
      $log.warn(m);
    };

    return {
      subscribe: subscribe

    };
  }]);