angular.module('kamonDashboard')
  .factory('HistogramSnapshot', function() {

    var HistogramSnapshot = function(records) {
      this.records = records;
      this.nrOfMeasurements = _.reduce(this.records, function(acc, rec){ return acc + rec.count; }, 0);
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
          mergedRecords[mergeIndex++] = {
            level: this.records[i].level,
            count: this.records[i].count + that.records[thatIndex].count
          };
          thatIndex += 1;

        } else {
          mergedRecords[mergeIndex++] = this.records[i];
        }
      }

      while(thatIndex < nrOfRecordsInThat) {
        mergedRecords[mergeIndex++] = that.records[thatIndex++];
      }

      this.records = mergedRecords;
      this.nrOfMeasurements = _.reduce(this.records, function(acc, rec){ return acc + rec.count; }, 0);

      return this;
    };


    HistogramSnapshot.prototype.substract = function(that) {
      var nrOfRecordsInThis = this.records.length;
      var thisIndex = 0;

      for(i = 0; i < that.records.length; i++) {
        while(thisIndex < nrOfRecordsInThis && this.records[thisIndex].level < that.records[i].level) {
          thisIndex++;
        }

        if(this.records[thisIndex].level == that.records[i].level) {
          var newCount = this.records[thisIndex].count - that.records[i].count;

          // Silently ignore underflowing the counts and leaving the unused node.
          if(newCount < 0) 
            newCount = 0;

          this.records[thisIndex].count = newCount;
        }
      }

      this.nrOfMeasurements = _.reduce(this.records, function(acc, rec){ return acc + rec.count; }, 0);
      return this;
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