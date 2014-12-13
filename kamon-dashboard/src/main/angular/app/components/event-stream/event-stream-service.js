angular.module('kamonDashboard')
  .factory('EventStream', ['$log', 'EventSubscriptions', function($log, EventSubscriptions) {
    var eventStream = {};
    var subscriptions = EventSubscriptions.create();
    var sse = new EventSource("http://127.0.0.1:9099/event-stream");
    
    sse.onmessage = function(event) {
      var eventData = JSON.parse(event.data);
      subscriptions.fireEvent(eventData.type, eventData.payload);
      //$log.info('Received Data: ');
      //$log.info(eventData.payload);
    };

    eventStream.subscribe = function(topic, callback) {
      //$log.info('Subscribing to topic ' + topic);
      return subscriptions.subscribe(topic, callback);
    };

    eventStream.fireEvent = function(topic, event) {
      //$log.info('Firing event');
      subscriptions.fireEvent(topic, event);
    };

    return eventStream;
  }]);