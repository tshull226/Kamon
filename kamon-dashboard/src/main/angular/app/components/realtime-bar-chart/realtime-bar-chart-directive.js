angular.module('kamonDashboard')
  .directive('krBarChart', ['$log', function($log) {

    // TODO: Take the X and Y units from the attributes.

    function _createXScale(timelineDimension) {
      var rangeEnd = _.now();
      var minimunRangeStart = rangeEnd - (1 * 60 * 1000); // 30 minutes, in milliseconds.
      var rangeStart = minimunRangeStart;
      var earliestTimestamp = timelineDimension.bottom(1);

      if(earliestTimestamp.length == 1 && rangeStart > earliestTimestamp[0]) {
        rangeStart = earliestTimestamp[0];
      }

      return d3.time.scale().domain([new Date(rangeStart), new Date(rangeEnd)]);
    }

    function _validateAttributes(attr) {
      if(!_.isString(attr.krMode) || (attr.krMode != 'timeline' && attr.krMode != 'realtime-update')) {
        $log.error('Invalid krMode attribute [' + attr.krMode + '] for krBarChart.');
      }
    }

    function _createXScaleFromGroup(group) {
      var allValues = group.all();
      if(_(allValues).isEmpty()) {
        return d3.scale.linear().domain([0, 1]);
      } else {
        //console.log(allValues[0]);
        //console.log('new scale from: ' + _(allValues).first().key + ' to ' + _(allValues).last().key);
        return d3.scale.linear().domain([_(allValues).first().key, _(allValues).last().key]);
      }
    }


    function link(scope, element, attr) {
      var assignedDomID = 'kr-' + _.uniqueId();
      var idSelector = '#' + assignedDomID;
      element.attr('id', assignedDomID);

      _validateAttributes(attr);

      //console.log(scope.group.all());

      var barChart = dc.barChart(idSelector);
      barChart
        .height(300)
        .width(900)
        .elasticY(true)
        .dimension(scope.dimension)
        .group(scope.group)
        .x(_createXScaleFromGroup(scope.group))
        .xUnits(function(start, end, domain) {
          return 60;
        }).elasticX(true);
        //.x(_createXScale(scope.dimension));


      barChart.render();

      // Watch the ticker for realtime changes
      scope.$watch(scope.ticker, function(newValue) {
        barChart.x(_createXScaleFromGroup(scope.group)).redraw();

        // TODO: better explanation
        // The chart will only redraw the X axis if the data has changed but it can happen
        // that the entity being shown stops reporting and we still need to roll the chart
        // as the timeline moves. This dirty forces the xAxis to be re-rendered.
        barChart.renderXAxis(barChart.g());
      });
    }


    return {
      restrict: 'E',
      link: link,
      scope: {
        dimension: '=krDimension',
        group: '=krGroup',
        ticker: '=krTicker',
        mode: '@krMode'
      }
    };

  }]);