angular.module('kamonDashboard')
  .factory('HistogramSnapshotFactory', function() {

    var HistogramSnapshot = function(records) {
      this.records = records;
      this.nrOfMeasurements = _.reduce(this.records, function(acc, rec) { 
        return acc + rec.count; 
      }, 0);  
    };


    HistogramSnapshot.prototype.add = function(that) {
      var nrOfRecordsInThat = that.records.length;
      var mergedRecords = [];
      var mergeIndex = 0;
      var thatIndex = 0;

      for(i = 0; i < this.records.length; i++) {
        while(thatIndex < nrOfRecordsInThat && this.records[i].level > that.records[thatIndex].level) {
          mergedRecords[mergeIndex++] = that.records[thatIndex];
          thatIndex += 1;
        }

        if(thatIndex < nrOfRecordsInThat && that.records[thatIndex].level == this.records[i].level) {
          var newCount = this.records[i].count + that.records[thatIndex].count;
          mergedRecords[mergeIndex++] = {
            level: this.records[i].level,
            count: newCount
          };

          thatIndex += 1;

        } else mergedRecords[mergeIndex++] = this.records[i];
      }

      while(thatIndex < nrOfRecordsInThat) {
        mergedRecords[mergeIndex++] = that.records[thatIndex++];
      }

      return new HistogramSnapshot(mergedRecords);
    };


    HistogramSnapshot.prototype.substract = function(that) {
      var nrOfRecordsInThis = this.records.length;
      var thisIndex = 0;

      var newRecords = [];
      var newRecordIndex = 0;

      for(i = 0; i < that.records.length; i++) {
        while(thisIndex < nrOfRecordsInThis && this.records[thisIndex].level < that.records[i].level) {
          newRecords.push(this.records[thisIndex++]);
        }

        if(this.records[thisIndex].level == that.records[i].level) {
          var newCount = this.records[thisIndex].count - that.records[i].count;

          // This silently ignores underflowing the counts at a given level, which should never happen
          // since this substract function is only meant to be used with crossfilter groups.
          if(newCount > 0) {
            newRecords.push({
              level: this.records[thisIndex].level,
              count: newCount
            });
          }

          thisIndex++;
        } 
      }

      // Add anything from this is left form this
      while(thisIndex < nrOfRecordsInThis) {
        newRecords.push(this.records[thisIndex++]);
      }

      return new HistogramSnapshot(newRecords);
    };

    HistogramSnapshot.prototype.recordsIterator = function() {
      var _index = 0;
      var _records = this.records;

      return {
        hasNext: function() {
          return _index < _records.length;
        },
        next: function() {
          return _records[_index++];
        }
      };
    };


    return {
      create: function(recordings) {
        return new HistogramSnapshot(recordings);
      },

      add:  function(left, right) {
        return left.add(right);
      },

      substract:  function(left, right) {
        return left.substract(right);
      },

      empty:  function() {
        return new HistogramSnapshot([]);
      }
    };
  });