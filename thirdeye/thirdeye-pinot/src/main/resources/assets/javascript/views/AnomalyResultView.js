
function AnomalyResultView(anomalyResultModel) {

  // model
  this.anomalyResultModel = anomalyResultModel;

  this.timeRangeConfig = {
      startDate : this.anomalyResultModel.startDate,
      endDate : this.anomalyResultModel.endDate,
      dateLimit : {
        days : 60
      },
      showDropdowns : true,
      showWeekNumbers : true,
      timePicker : true,
      timePickerIncrement : 5,
      timePicker12Hour : true,
      ranges : {
        'Last 24 Hours' : [ moment(), moment() ],
        'Yesterday' : [ moment().subtract(1, 'days'), moment().subtract(1, 'days') ],
        'Last 7 Days' : [ moment().subtract(6, 'days'), moment() ],
        'Last 30 Days' : [ moment().subtract(29, 'days'), moment() ],
        'This Month' : [ moment().startOf('month'), moment().endOf('month') ],
        'Last Month' : [ moment().subtract(1, 'month').startOf('month'), moment().subtract(1, 'month').endOf('month') ]
      },
      buttonClasses : [ 'btn', 'btn-sm' ],
      applyClass : 'btn-primary',
      cancelClass : 'btn-default'
    };

  // Compile HTML template
  var anomalies_template = $("#anomalies-template").html();
  this.anomalies_template_compiled = Handlebars.compile(anomalies_template);

  // events
  this.hideDataRangePickerEvent = new Event(this);
  this.rootCauseAnalysisButtonClickEvent = new Event();
  this.showDetailsLinkClickEvent = new Event();
  this.anomalyFeedbackSelectEvent = new Event();

}

AnomalyResultView.prototype = {
  init : function() {

  },

  render : function() {

    var anomalies = this.anomalyResultModel.anomalies;
    console.log("anomalies");
    console.log(anomalies);

    var result_anomalies_template_compiled = this.anomalies_template_compiled(anomalies);
    $("#anomalies-place-holder").html(result_anomalies_template_compiled);
    this.renderAnomaliesTab(anomalies);

    // METRIC SELECTION
    var metrics = this.anomalyResultModel.metrics;
    console.log("metrics");
    console.log(metrics);

    $('#metric-search-input').autocomplete({
      lookup : metrics,
      onSelect : function(suggestion) {
        alert('You selected: ' + suggestion.value + ', ' + suggestion.data);
      }
    });


    // TIME RANGE SELECTION
    this.timeRangeConfig.startDate = this.anomalyResultModel.startDate;
    this.timeRangeConfig.endDate = this.anomalyResultModel.endDate;
    function cb(start, end) {
      $('#anomalies-time-range span').addClass("time-range").html(start.format('MMM D, ') + start.format('hh:mm a') + '  &mdash;  ' + end.format('MMM D, ') + end.format('hh:mm a'));
    }
    $('#anomalies-time-range').daterangepicker(this.timeRangeConfig, cb);
    cb(this.timeRangeConfig.startDate, this.timeRangeConfig.endDate);

    this.setupListenerOnDateRangePicker();

  },

  renderAnomaliesTab: function (anomalies) {
    for (var idx = 0; idx < anomalies.length; idx++) {
      var anomaly = anomalies[idx];
      console.log(anomaly);

      var currentRange = anomaly.currentStart + " - " + anomaly.currentEnd;
      var baselineRange = anomaly.baselineStart + " - " + anomaly.baselineEnd;
      $("#current-range-"+idx).html(currentRange);
      $("#baseline-range-"+idx).html(baselineRange);

      var date = ['date'].concat(anomaly.dates);
      var currentValues = ['current'].concat(anomaly.currentValues);
      var baselineValues = ['baseline'].concat(anomaly.baselineValues);
      var chartColumns = [ date, currentValues, baselineValues];
      console.log(chartColumns);

      var regionStart = anomaly.anomalyRegionStart;
      var regionEnd = anomaly.anomalyRegionEnd;
      $("#region-"+idx).html(regionStart + " - " + regionEnd)

      var current = anomaly.current;
      var baseline = anomaly.baseline;
      $("#current-value-"+idx).html(current);
      $("#baseline-value-"+idx).html(baseline);

      var dimension = anomaly.anomalyFunctionDimension;
      $("#dimension-"+idx).html(dimension)

      if (anomaly.anomalyFeedback) {
        $("#anomaly-feedback-"+idx+" select").val(anomaly.anomalyFeedback);
      }


      // CHART GENERATION
      var chart = c3.generate({
        bindto : '#anomaly-chart-'+idx,
        data : {
          x : 'date',
          columns : chartColumns,
          type : 'spline'
        },
        legend : {
          show : false,
          position : 'top'
        },
        axis : {
          y : {
            show : true
          },
          x : {
            type : 'timeseries',
            show : true
          }
        },
        regions : [ {
          axis : 'x',
          start : regionStart,
          end : regionEnd
        } ]
      });

      this.setupListenersOnAnomaly(idx, anomaly);
    }

  },

  dataEventHandler: function(e) {
    var currentTargetId = e.currentTarget.id;
    if (currentTargetId.startsWith('root-cause-analysis-button-')) {
      this.rootCauseAnalysisButtonClickEvent.notify(e.data);
    } else if (currentTargetId.startsWith('show-details-')) {
      this.showDetailsLinkClickEvent.notify(e.data);
    } else if (currentTargetId.startsWith('anomaly-feedback-')) {
      var option = $("#" + currentTargetId + " option:selected").text();
      e.data['feedback'] = option;
      this.anomalyFeedbackSelectEvent.notify(e.data);
    }
  },

  hideDataRangePickerEventHandler: function(e, dataRangePicker) {
    console.log("hide event");
    console.log(e);
    console.log(dataRangePicker);
    var dateRangeParams = {
        startDate: dataRangePicker.startDate,
        endDate: dataRangePicker.endDate
     }
    this.hideDataRangePickerEvent.notify(dateRangeParams);
  },

  setupListenerOnDateRangePicker: function() {
    $('#anomalies-time-range').on('hide.daterangepicker', this.hideDataRangePickerEventHandler.bind(this));
  },

  setupListenersOnAnomaly : function(idx, anomaly) {
    var rootCauseAnalysisParams = {
                    metric: anomaly.metric,
                    rangeStart: anomaly.currentStart,
                    rangeEnd: anomaly.currentEnd,
                    dimension: anomaly.anomalyFunctionDimension
                 }
    $('#root-cause-analysis-button-'+idx).click(rootCauseAnalysisParams, this.dataEventHandler.bind(this));
    var showDetailsParams = {
        anomalyId: anomaly.anomalyId,
        metric: anomaly.metric,
        rangeStart: anomaly.currentStart,
        rangeEnd: anomaly.currentEnd,
        dimension: anomaly.anomalyFunctionDimension
     }
    $('#show-details-'+idx).click(showDetailsParams, this.dataEventHandler.bind(this));
    var anomalyFeedbackParams = {
        anomalyId: anomaly.anomalyId
     }
    $('#anomaly-feedback-'+idx).change(anomalyFeedbackParams, this.dataEventHandler.bind(this));
  }

};
