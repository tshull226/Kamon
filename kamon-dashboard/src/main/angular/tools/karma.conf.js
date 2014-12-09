module.exports = function(config) {
  config.set({
    basePath: '../',
    files: [
      'tools/dist/assets/js/vendor.js',
      'tools/dist/assets/js/app.js',
      'app/**/*.test.js'
    ],
    frameworks: ['jasmine'],
    browsers: ['Chrome'],
    plugins: [
      'karma-chrome-launcher',
      'karma-firefox-launcher',
      'karma-jasmine',
      'karma-junit-reporter'
    ],
    junitReporter: {
      outputFile: 'test_out/unit.xml',
      suite: 'unit'
    }
  });
};