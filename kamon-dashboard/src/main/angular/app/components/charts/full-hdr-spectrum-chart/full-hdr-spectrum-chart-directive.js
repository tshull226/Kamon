angular.module('kamonDashboard')
  .directive('krFullHdrSpectrumChart', ['$log', function($log) {

    var RANGE = 1000;

    function _validateAttributes(attr) {
      if(!_.isString(attr.krMode) || (attr.krMode != 'timeline' && attr.krMode != 'realtime-update')) {
        $log.error('Invalid krMode attribute [' + attr.krMode + '] for krLineChart.');
      }
    }


    function _percentileOf(xValue) {
      return 1 - (1 / xValue);
    }

    function _validateHistogram(histogram) {
      if(histogram.records.length > 1) {
        var lastViewed = histogram.records[0];
        var tail = _(histogram.records).tail();
        var counted = lastViewed.count;
        var faults = 0;

        _(tail).each(function (value) {
          if(value.level <= lastViewed.level) {
            faults++;
          }
          counted += value.count;
          lastViewed = value;
        })

        if(faults > 0) {
          console.log('Faulty histogram');
        }
      }
    }


    function _buildFullSpectrumGroup(logScale, histogramGroup, lineChart) {
      function _buildDistributionData() {
        var xValues = _(RANGE).times(function(i) { 
          return logScale.invert(i); 
        });

        var histogram = histogramGroup.value();
        _validateHistogram(histogram);


        var totalCount = histogram.nrOfMeasurements;
        var recordsIterator = histogram.recordsIterator();

        var accumulatedCount = 0;
        var currentLevel = 0;

        var distributionData = _(xValues).map(function(xValue) {
          var percentile = _percentileOf(xValue);
          var percentileCountLimit = percentile * totalCount;

          while(recordsIterator.hasNext() && accumulatedCount < percentileCountLimit) {
            var record = recordsIterator.next();
            currentLevel = record.level;
            accumulatedCount += record.count;
          }

          return {
            key: xValue,
            value: currentLevel
          };
        });

        console.log('Max Value: ' + _yValueLabel(currentLevel));

        // Add 10% more to the domain limit to make sure the max value won't be at the very edge of the graph.
        var maxYValue = Math.floor(currentLevel * 1.10);
        var chartHeight = lineChart.height();

        // Force re-render of the yAxis to make it fit the current data set.
        lineChart.y().domain([0, maxYValue]);
        lineChart.renderYAxis(lineChart.g());

        return distributionData;
      }

      return {
        all: function() { return _buildDistributionData(); }
      };
    }


    function _createXTickValues(numberOfNines) {
      return _(numberOfNines).times(function(n) {
        return Math.pow(10, n + 1);
      });
    }

    function _yValueLabel(value) {
      var magnitude = Math.log10(value);

      if(value === 0) {
        return ''; 
      } else if(magnitude < 3) {
        return value + ' ns';
      } else if(magnitude < 6) {
        return (value / 1000) + ' μs';
      } else if(magnitude < 9) {
        return (value / 1000000) + ' ms';
      } else {
        return (value / 1000000000) + ' s';
      }
    }


    function _xValueLabel(value) {
      var numberOfNinesForValue = Math.log10(value);
      switch(numberOfNinesForValue) {
        case 1: return '90%';
        case 2: return '99%';
        case 3: return '99.9%';
        case 4: return '99.99%';
        case 5: return '99.999%';
        case 6: return '99.9999%';
        case 7: return '99.99999%';
        default: return 'Better call Saul';
      }
    }


    function _buildLogScale(numberOfNines) {
      var domainLimit = Math.pow(10, numberOfNines);
      return d3.scale.log()
              .domain([1, domainLimit])
              .range([0, RANGE]);
    }


    function _targetElementSelector(element) {
      var assignedDomID = 'kr-' + _.uniqueId();
      element.attr('id', assignedDomID);
      return '#' + assignedDomID;
    }


    function link(scope, element, attr) {
      _validateAttributes(attr);

      var numberOfNines = attr.krNumberOfNines;

      // Apparently the chart is mutating the scale internally and that screws our use of it when building 
      // the distribution group. That's why we have two separate scales with the same initial config.
      var logScale = _buildLogScale(numberOfNines);
      var chartScale = _buildLogScale(numberOfNines);

      var lineChart = dc.lineChart(_targetElementSelector(element));
      lineChart
        .height(270)
        .width(650)
        .dimension(scope.dimension)
        .group(_buildFullSpectrumGroup(logScale, scope.group, lineChart))
        .xUnits(function(start, end, domain) {
          return RANGE;
        })
        .x(chartScale)
        .renderVerticalGridLines(true)
        .renderHorizontalGridLines(true);

      lineChart.xAxis()
        .tickFormat(_xValueLabel)
        .tickValues(_createXTickValues(numberOfNines));

      lineChart.yAxis()
        .tickFormat(_yValueLabel);

      lineChart.margins().left = 100;
      lineChart.margins().top = 50;

      lineChart.render();
      lineChart.renderYAxis(lineChart.g());

      // Watch the ticker for realtime changes
      scope.$watch(scope.ticker, function(newValue) {
        lineChart
          .group(_buildFullSpectrumGroup(logScale, scope.group, lineChart))
          .redraw();
      });
    }


    return {
      restrict: 'E',
      link: link,
      scope: {
        dimension: '=krDimension',
        group: '=krGroup',
        ticker: '=krTicker',
        mode: '@krMode',
        numberOfNines: '@krNumberOfNines'
      }
    };

  }]);