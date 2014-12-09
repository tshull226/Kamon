angular.module('kamonDashboard')
  .factory('EventSubscriptions', function() {
    var EventSubscriptionsManager = function() {
      this.subscriptions = [];
    };

    EventSubscriptionsManager.prototype.subscribe = function(eventType, callback) {
      var manager = this;
      var subscriberID = _.uniqueId();
      this.subscriptions.push({ id: subscriberID, type: eventType, cb: callback });

      return {
        cancel: function() {
          manager.subscriptions = _.filter(manager.subscriptions, function(s) { return s.id !== subscriberID; });
        }
      };
    };

    EventSubscriptionsManager.prototype.fireEvent = function(eventType, event) {
      _.each(this.subscriptions, function(sub) {
        if(angular.equals(sub.type, eventType))
          sub.cb(event);
      });
    };

    EventSubscriptionsManager.prototype.nrOfSubscribers = function() {
      return this.subscriptions.length;
    };


    var EventSubscriptions = {};
    EventSubscriptions.create = function() {
      return new EventSubscriptionsManager();
    };

    return EventSubscriptions;
  });