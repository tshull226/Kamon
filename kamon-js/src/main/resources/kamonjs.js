window.kamonjs = window.kamonjs || {};

(function () {

    kamonjs.init = function (config) {
        this.performance = window.performance || window.msPerformance || window.webkitPerformance || window.mozPerformance;
        window.onload = this.scheduler
    };

    kamonjs.metrics = [];

    kamonjs.addMetric = function (metric) {
        if (typeof metric !== 'object') return new Error('metric is not an Object.');
        if (!metric.id)    return new Error('Id can\'t be empty.')
        if (!metric.hasOwnProperty('value')) return new Error('Value can\'t be empty.')

        // if metric is disabled do nothing
        //if ( this.disabledMetrics[metric.id] ) return;

        this.metrics.push(metric)
    }

    kamonjs.scheduler = function () {
        setTimeout(function () {
            if (kamonjs.performance) {
                kamonjs.runMetrics('performance')
            }
            kamonjs.runMetrics('others')

            kamonjs.sendMetrics(kamonjs.metrics)
        }, 1000)
    }


    var metrics = { performance: [], others: [] }

    kamonjs.runMetrics = function (type) {
        for (var i = 0; i < metrics[type].length; i++) {
            metrics[type][i]()
        }
        delete metrics[type];
    }

    kamonjs.sendMetrics = function (metrics) {
        var xmlhttp = new XMLHttpRequest();   // new HttpRequest instance
        xmlhttp.open("POST", "/json-handler");
        xmlhttp.setRequestHeader("Content-Type", "application/json;charset=UTF-8");
        xmlhttp.send(JSON.stringify(metrics));
    }

    metrics.performance.push(function () {
        kamonjs.addMetric({
            id: 'loadTime',
            value: (kamonjs.performance.timing.loadEventStart - kamonjs.performance.timing.navigationStart),
            label: 'Load Time'
        })
    })

    metrics.performance.push(function () {
        kamonjs.addMetric({
            id: 'latency',
            value: ( kamonjs.performance.timing.responseStart - kamonjs.performance.timing.connectStart ),
            label: 'Latency'
        })
    })

    metrics.performance.push(function () {
        var max = Math.round(( kamonjs.performance.timing.loadEventStart - kamonjs.performance.timing.navigationStart ) * 0.8)

        kamonjs.addMetric({
            id: 'frontEnd',
            value: (kamonjs.performance.timing.loadEventStart - kamonjs.performance.timing.responseEnd ),
            label: 'Front End'
        })
    })

    metrics.performance.push(function () {
        var max = Math.round(( kamonjs.performance.timing.loadEventStart - kamonjs.performance.timing.navigationStart ) * 0.2)

        kamonjs.addMetric({
            id: 'backEnd',
            value: (kamonjs.performance.timing.responseEnd - kamonjs.performance.timing.navigationStart ),
            label: 'Back End'
        })
    })

    metrics.performance.push(function () {
        kamonjs.addMetric({
            id: 'respnseDuration',
            value: (kamonjs.performance.timing.responseEnd - kamonjs.performance.timing.responseStart ),
            unit: 'ms',
            label: 'Response Duration'
        })
    })

    metrics.performance.push(function () {
        kamonjs.addMetric({
            id: 'requestDuration',
            value: (kamonjs.performance.timing.responseStart - kamonjs.performance.timing.requestStart ),
            unit: 'ms',
            label: 'Request Duration'
        })
    })

    metrics.performance.push(function () {
        if (!kamonjs.performance.navigation) return
        kamonjs.addMetric({
            id: 'redirectCount',
            value: kamonjs.performance.navigation.redirectCount,
            label: 'Redirects'
        })
    })

    metrics.performance.push(function () {
        kamonjs.addMetric({
            id: 'loadEventTime',
            value: (kamonjs.performance.timing.loadEventEnd - kamonjs.performance.timing.loadEventStart ),
            unit: 'ms',
            label: 'Load Event duration'
        })
    })

    metrics.performance.push(function () {
        kamonjs.addMetric({
            id: 'domContentLoaded',
            value: (kamonjs.performance.timing.domContentLoadedEventStart - kamonjs.performance.timing.domInteractive ),
            unit: 'ms',
            label: 'DOM Content loaded'
        })
    })


    metrics.performance.push(function () {
        kamonjs.addMetric({
            id: 'processing',
            value: kamonjs.performance.timing.loadEventStart - kamonjs.performance.timing.domLoading,
            unit: 'ms',
            label: 'Processing Duration'
        })
    })

    //others metrics

    metrics.others.push(function () {
        kamonjs.addMetric({
            id: 'numOfEl',
            value: document.documentElement.querySelectorAll('*').length,
            label: 'DOM elements'
        })
    })

    metrics.others.push(function () {
        kamonjs.addMetric({
            id: 'cssCount',
            value: document.querySelectorAll('link[rel="stylesheet"]').length,
            label: 'CSS'
        })
    })

    metrics.others.push(function () {
        kamonjs.addMetric({
            id: 'jsCount',
            value: document.querySelectorAll('script').length,
            label: 'JavaScript'
        })
    })

    metrics.others.push(function () {
        kamonjs.addMetric({
            id: 'imgCount',
            value: document.querySelectorAll('img').length,
            label: 'Images'
        })
    })

    metrics.others.push(function () {
        var count = 0
        var images = document.querySelectorAll('img[src]')

        for (var i = 0; i < images.length; i++) {
            if (images[i].src.match(/^data:+/)) count++
        }
        kamonjs.addMetric({
            id: 'dataURIImagesCount',
            value: count,
            label: 'Data URI images'
        })
    })

    metrics.others.push(function () {
        kamonjs.addMetric({
            id: 'inlineCSSCount',
            value: document.querySelectorAll('style').length,
            label: 'Inline CSS'
        })

    })

    metrics.others.push(function () {
        var js = document.querySelectorAll('script')
        var count = 0
        for (var i = 0; i < js.length; i++) {
            if (!js[i].src) count++
        }

        kamonjs.addMetric({
            id: 'inlineJSCount',
            value: count,
            label: 'Inline JavaScript'
        })
    })
})()

kamonjs.init();
sleepFor(4000);

function sleepFor(sleepDuration) {
    var now = new Date().getTime();
    while (new Date().getTime() < now + sleepDuration) { /* do nothing */
    }
}