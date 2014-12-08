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
var reload      = browserSync.reload;


var jsFiles = [
  '../app/app.js', 
  '../app/**/module.js', 
  '../app/**/*.js'
]

var vendorJsFiles = [
  '../vendor/angular/angular.js',
  '../vendor/angular-route/angular-route.js',
  '../vendor/underscore/underscore.js',
  '../vendor/d3/d3.min.js',
  '../vendor/crossfilter.min.js',
  '../vendor/dcjs/dcjs.min.js'
]

var viewFiles = [
  '../index.html',
  '../app/**/*.html'
]


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


gulp.task('views', function() {
  return gulp.src(viewFiles)
    .pipe(gulp.dest('./dist'))
})


gulp.task('clean', function (cb) {
  del(['./dist/**', '!./dist'], cb);
});


gulp.task('build', ['js', 'vendor-js', 'views']);


gulp.task('start-server', function() {
  browserSync({
    server: {
      baseDir: './dist'
    }
  });

  gulp.watch(jsFiles.concat(viewFiles), ['build']);
});


gulp.task('serve', ['build', 'start-server'])
