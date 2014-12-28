// Extracted from Jeff Dickey's awesome angular-boilerplate (https://github.com/dickeyxxx/angular-boilerplate)
'use strict';

var gulp        = require('gulp')
var concat      = require('gulp-concat')
var plumber     = require('gulp-plumber')
var sourcemaps  = require('gulp-sourcemaps')
var uglify      = require('gulp-uglify')
var browserSync = require('browser-sync')
var del         = require('del')
var jshint      = require('gulp-jshint')
var stylish     = require('jshint-stylish')
var karma       = require('karma').server;
var reload      = browserSync.reload;


var jsFiles = [
  '../app/app.js', 
  '../app/**/module.js', 
  '../app/**/*.js',
  '!../app/**/*.test.js',
  '!../app/config*.js'
]

var vendorJsFiles = [
  '../vendor/angular/angular.js',
  '../vendor/angular-route/angular-route.js',
  '../vendor/angular-mocks/angular-mocks.js',
  '../vendor/underscore/underscore.js',
  '../vendor/d3/d3.min.js',
  '../vendor/crossfilter/crossfilter.min.js',
  '../vendor/dcjs/dc.js',
  '../vendor/blueimp-md5/js/md5.min.js',
  '../vendor/angular-dc/dist/angular-dc.js'
]

var viewFiles = [
  '../index.html',
  '../app/**/*.html'
]

var vendorCssFiles = [
  '../vendor/dcjs/dc.css',
]

var optionConfigFile = [ '../app/config.js' ]


gulp.task('js', function () {
  return gulp.src(jsFiles)
    .pipe(jshint())
    .pipe(jshint.reporter(stylish))
    .pipe(sourcemaps.init())
      .pipe(plumber())
      .pipe(concat('app.js'))
      //.pipe(uglify())
    .pipe(sourcemaps.write())
    .pipe(gulp.dest('./dist/assets/js'))
    .pipe(reload({ stream:true }))
})


gulp.task('vendor-js', function () {
  return gulp.src(vendorJsFiles)
    .pipe(plumber())
    .pipe(concat('vendor.js'))
    .pipe(gulp.dest('./dist/assets/js'))
    .pipe(reload({ stream:true }))
})

gulp.task('vendor-css', function () {
  return gulp.src(vendorCssFiles)
    .pipe(plumber())
    .pipe(concat('vendor.css'))
    .pipe(gulp.dest('./dist/assets/css'))
    .pipe(reload({ stream:true }))
})


gulp.task('views', function() {
  return gulp.src(viewFiles)
    .pipe(gulp.dest('./dist'))
})


gulp.task('clean', function (cb) {
  del(['./dist/**', '!./dist'], cb);
});


/**
 * Run test once and exit
 */
gulp.task('test', function (done) {
  karma.start({
    configFile: __dirname + '/karma.conf.js',
    singleRun: true
  }, done);
});

/**
 * Watch for file changes and re-run tests on each change
 */
gulp.task('tdd', function (done) {
  karma.start({
    configFile: __dirname + '/karma.conf.js'
  }, done);

  gulp.watch(jsFiles, ['js']);
});


gulp.task('start-server', function() {
  browserSync({
    server: {
      baseDir: './dist'
    }
  });

  gulp.watch(jsFiles.concat(viewFiles), ['build']);
  gulp.watch(optionConfigFile, ['copy-optional-config'])
});


// We might have a config.js file that we want to use for testing via `gulp serve` but that we definitely
// don't want to ship if we are doing a release build via the `sbt-build` task.
gulp.task('copy-optional-config', function() {
  return gulp.src('../app/config.js')
    .pipe(gulp.dest('./dist'))
    .pipe(reload({ stream:true }))
})

gulp.task('build', ['js', 'vendor-js', 'vendor-css', 'views', 'copy-optional-config']);

gulp.task('serve', ['build', 'start-server'])




/**
  *  Tasks that will be invoked from SBT when building:
  */

gulp.task('copy-dist-to-resources', function(cb) {
  return gulp.src(['./dist/**', '!./dist/config.js'])
    .pipe(gulp.dest('../../resources/dashboard-webapp/'))
});

gulp.task('sbt-build', ['build', 'copy-dist-to-resources']);