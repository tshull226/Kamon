describe('Event Subscription Service', function() {
  
  beforeEach(module('kamonDashboard'));

  it('should provide a create function for getting a new, empty subscriptions manager', inject(function(EventSubscriptions) {
    var subsManager = EventSubscriptions.create();
    expect(subsManager.nrOfSubscribers()).toBe(0);
  }));


  describe('EventSubscriptionManager', function() {

    it('register new subscribers via the .subscribe function', inject(function(EventSubscriptions) {
      var subsManager = EventSubscriptions.create();
      expect(subsManager.nrOfSubscribers()).toBe(0);

      subsManager.subscribe('cool', function() {});
      expect(subsManager.nrOfSubscribers()).toBe(1);

      subsManager.subscribe('cool', function() {});
      expect(subsManager.nrOfSubscribers()).toBe(2);
    }));


    it('remove subscribers by calling .cancel in the object returned from .subscribe', inject(function(EventSubscriptions) {
      var subsManager = EventSubscriptions.create();
      expect(subsManager.nrOfSubscribers()).toBe(0);

      var coolSubscription = subsManager.subscribe('cool', function() {});
      var sickSubscription = subsManager.subscribe('sick', function() {});
      expect(subsManager.nrOfSubscribers()).toBe(2);

      sickSubscription.cancel();
      expect(subsManager.nrOfSubscribers()).toBe(1);
    }));


    it('execute all callbacks registered for a given topic', inject(function(EventSubscriptions) {
      var subsManager = EventSubscriptions.create();
      var coolCallback = jasmine.createSpy('coolCallback');
      var sickCallback = jasmine.createSpy('sickCallback');

      subsManager.subscribe('cool', coolCallback);
      var sickSubscription = subsManager.subscribe('sick', sickCallback);
      
      subsManager.fireEvent('cool', 'some cool data');
      expect(coolCallback.calls.count()).toEqual(1);
      expect(coolCallback).toHaveBeenCalledWith('some cool data');
      expect(sickCallback.calls.count()).toEqual(0);

      sickSubscription.cancel();
      subsManager.fireEvent('sick', 'some sick data');
      expect(sickCallback.calls.count()).toEqual(0);
    }));
  });

  
});