angular.module('kamonDashboard')
  .directive('krLineChart', ['$log', function($log) {

    // TODO: Take the X and Y units from the attributes.

    function _createXScale(timelineDimension) {
      var rangeEnd = _.now();
      var minimunRangeStart = rangeEnd - (10 * 60 * 1000); // 30 minutes, in milliseconds.
      var rangeStart = minimunRangeStart;
      var earliestTimestamp = timelineDimension.bottom(1);

      if(earliestTimestamp.length == 1 && rangeStart > earliestTimestamp[0]) {
        rangeStart = earliestTimestamp[0];
      }

      return d3.time.scale().domain([new Date(rangeStart), new Date(rangeEnd)]);
    }

    function _validateAttributes(attr) {
      if(!_.isString(attr.krMode) || (attr.krMode != 'timeline' && attr.krMode != 'realtime-update')) {
        $log.error('Invalid krMode attribute [' + attr.krMode + '] for krLineChart.');
      }
    }


    function link(scope, element, attr) {
      var assignedDomID = 'kr-' + _.uniqueId();
      var idSelector = '#' + assignedDomID;
      element.attr('id', assignedDomID);

      _validateAttributes(attr);

      var lineChart = dc.lineChart(idSelector);
      lineChart
        .height(270)
        .width(650)
        .elasticY(true)
        .dimension(scope.dimension)
        .group(scope.group)
        .interpolate('step')
        .xUnits(d3.time.milliseconds)
        .x(_createXScale(scope.dimension));


        lineChart.margins().left = 100;


      lineChart.render();

      // Watch the ticker for realtime changes
      scope.$watch(scope.ticker, function(newValue) {
        //lineChart.brush().event(lineChart.g().select("g.brush"))
        lineChart.x(_createXScale(scope.dimension)).redraw();

        // TODO: better explanation
        // The chart will only redraw the X axis if the data has changed but it can happen
        // that the entity being shown stops reporting and we still need to roll the chart
        // as the timeline moves. This dirty forces the xAxis to be re-rendered.
        lineChart.renderXAxis(lineChart.g());
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