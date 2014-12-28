angular.module('kamonDashboard')
  .factory('EventStream', ['$log', 'EventSubscriptions', 'Configuration', function($log, EventSubscriptions, config) {
    var eventStream = {};
    var subscriptions = EventSubscriptions.create();
    var sse = new EventSource(config.eventStream);
    
    sse.onmessage = function(event) {
      var eventData = JSON.parse(event.data);
      subscriptions.fireEvent(eventData.type, eventData.payload);
    };

    eventStream.subscribe = function(topic, callback) {
      return subscriptions.subscribe(topic, callback);
    };

    eventStream.fireEvent = function(topic, event) {
      subscriptions.fireEvent(topic, event);
    };

    return eventStream;
  }]);