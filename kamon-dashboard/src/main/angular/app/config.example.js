angular.module('kamonDashboard')
  .factory('Configuration', function() {
    return {
      eventStream: '/event-stream'
      retentionTimeMillis: 30000

    };
  });