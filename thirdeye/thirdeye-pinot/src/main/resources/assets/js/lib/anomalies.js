var anomaliesDisplayData = "";
var timeseriesDisplayData = "";
var metricLineChart = "";
var metricChangeChart = ""

function getFeedbackTypeString(feedbackType) {
    switch (feedbackType) {
        case 'ANOMALY':
            return "Confirmed Anomaly";
        case 'NOT_ANOMALY':
            return 'False Alarm';
        case 'ANOMALY_NO_ACTION':
            return 'Confirmed - Not Actionable';
        default:
            return feedbackType;
    }
}
function getFeedbackTypeFromString(feedbackTypeStr) {
    switch (feedbackTypeStr) {
        case 'Confirmed Anomaly':
            return "ANOMALY";
        case 'False Alarm':
            return 'NOT_ANOMALY';
        case 'Confirmed - Not Actionable':
            return 'ANOMALY_NO_ACTION';
        default:
            return feedbackTypeStr;
    }
}

function getAnomalies(tab) {

    //Get dataset anomaly metrics
    var url = "/dashboard/anomalies/metrics?dataset=" + hash.dataset;
    getData(url).done(function (anomalyMetricList) {

        //Define query params for timeseries and anomaly result query
        var compareMode = hash.fnCompareWeeks ?  hash.fnCompareWeeks : 1;
        var baselineStart = moment(parseInt(hash.currentStart)).subtract(compareMode, 'week');
        var baselineEnd = moment(parseInt(hash.currentEnd)).subtract(compareMode, 'week');
        var aggTimeGranularity = calcAggregateGranularity(hash.currentStart,hash.currentEnd);
        var dataset = hash.dataset;
        var currentStart = hash.currentStart;
        var currentEnd = hash.currentEnd;
        var metrics = hash.metrics;
        var timeSeriesUrl = "/dashboard/data/tabular?dataset=" + dataset //
            + "&currentStart=" + currentStart + "&currentEnd=" + currentEnd  //
            + "&baselineStart=" + baselineStart + "&baselineEnd=" + baselineEnd   //
            + "&aggTimeGranularity=" + aggTimeGranularity + "&metrics=" + metrics;

        var currentStartISO = moment(parseInt(currentStart)).toISOString();
        var currentEndISO = moment(parseInt(currentEnd)).toISOString();

        //If the dataset has anomaly function set up for the selected metric, query the anomaly results
        if(anomalyMetricList && anomalyMetricList.indexOf(metrics)> -1){

            var urlParams = "dataset=" + hash.dataset + "&startTimeIso=" + currentStartISO + "&endTimeIso=" + currentEndISO + "&metric=" + hash.metrics;
            urlParams += hash.filter ? "&filters=" + hash.filters : "";
            urlParams += hash.hasOwnProperty("anomalyFunctionId")  ?   "&id=" + hash.anomalyFunctionId : "";
            var anomaliesUrl = "/dashboard/anomalies/view?" + urlParams;

            //AJAX for anomaly result data
            getData(anomaliesUrl).done(function (anomalyData) {
                for (var i in anomalyData) {
                    if (anomalyData[i].feedback) {
                        if (anomalyData[i].feedback.feedbackType) {
                            anomalyData[i].feedback.feedbackType = getFeedbackTypeString(anomalyData[i].feedback.feedbackType);
                        }
                    }
                }
                anomaliesDisplayData = anomalyData;
                getTimeseriesData(anomaliesDisplayData);
            });
        } else {
            getTimeseriesData();
        }

        function getTimeseriesData(anomalyData) {

            if(anomalyData){
                var anomalyMetric = true;
            }
            var  anomalyData = anomalyData || [];
            getData(timeSeriesUrl).done(function (timeSeriesData) {
                timeseriesDisplayData = timeSeriesData;
                //Error handling when data is falsy (empty, undefined or null)
                if (!timeSeriesData) {
                    $("#" + tab + "-chart-area-error").empty();
                    var warning = $('<div></div>', { class: 'uk-alert uk-alert-warning' });
                    warning.append($('<p></p>', { html: 'Something went wrong. Please try and reload the page. Error: metric timeseries data =' + timeSeriesData  }));
                    $("#" + tab + "-chart-area-error").append(warning);
                    $("#" + tab + "-chart-area-error").show();
                    return
                } else {
                    $("#" + tab + "-chart-area-error").hide();
                    $("#" + tab + "-display-chart-section").empty();
                }
                var placeholder = "linechart-placeholder"
                renderTimeseriesArea(timeSeriesData, tab);
                drawMetricTimeSeries(timeSeriesData, anomalyData, tab, placeholder);

                if (anomalyMetric) {
                    $(".anomaly-metric-tip").hide();
                    renderAnomalyThumbnails(anomalyData, tab);
                }else{
                    tipToUser(tab)
                }

                //anomalyFunctionId and hash.fnCompareWeeks are only present in hash when anomaly
                // function run adhoc was requested on self service tab
                //needs to be removed to be able to view other functions in later queries on the anomalies view
                delete hash.anomalyFunctionId;
                delete hash.fnCompareWeeks;
            });
        };
    });
};

function renderTimeseriesArea(timeSeriesData, tab) {
    /* Handelbars template for time series legend */
    var result_metric_time_series_section = HandleBarsTemplates.template_metric_time_series_section(timeSeriesData);
    $("#" + tab + "-display-chart-section").append(result_metric_time_series_section);
}

function drawMetricTimeSeries(timeSeriesData, anomalyData, tab, placeholder) {

    var currentView = $("#" + tab + "-display-chart-section");

    //Unbind previous eventListeners
    currentView.off("click")
    currentView.off("change")

    var aggTimeGranularity = (window.datasetConfig.dataGranularity) ? window.datasetConfig.dataGranularity : "HOURS";
    var dateTimeFormat = "%I:%M %p";
    if (aggTimeGranularity == "DAYS" ) {
        dateTimeFormat = "%m-%d";
    }else if(timeSeriesData.summary.baselineEnd - timeSeriesData.summary.baselineStart > 86400000 ){
        dateTimeFormat = "%m-%d %I %p";
    }

    var lineChartPlaceholder = $("#"+ placeholder, currentView)[0];
    var barChartPlaceholder = $("#barchart-placeholder", currentView)[0];
    var metrics = timeSeriesData["metrics"];
    var lineChartData = {};
    var barChartData = {};
    var xTicksBaseline = [];
    var xTicksCurrent = [];
    var colors = {};
    var regions = [];

    for (var t = 0, len = timeSeriesData["timeBuckets"].length; t < len; t++) {
        var timeBucket = timeSeriesData["timeBuckets"][t]["currentStart"];
        var currentEnd = timeSeriesData["timeBuckets"][t]["currentEnd"];
        xTicksBaseline.push(timeBucket);
        xTicksCurrent.push(timeBucket);
    }

    for (var i = 0; i < anomalyData.length; i++) {
        var anomaly = anomalyData[i];

        var anomalyStart = anomaly.startTime;
        var anomalyEnd = anomaly.endTime;
        var anomayID = "anomaly-id-" + anomaly.id;
        regions.push({'axis': 'x', 'start': anomalyStart, 'end': anomalyEnd, 'class': 'regionX ' + anomayID });
    }
    lineChartData["time"] = xTicksCurrent;
    barChartData["time"] = xTicksCurrent;

    var colorArray;
    if (metrics.length < 10) {
        colorArray = d3.scale.category10().range();
    } else if (metrics.length < 20) {
        colorArray = d3.scale.category20().range();
    } else {
        colorArray = colorScale(metrics.length)
    }

    for (var i = 0, mlen = metrics.length; i < mlen; i++) {
        var metricBaselineData = [];
        var metricCurrentData = [];
        var deltaPercentageData = [];
        var indexOfBaseline = timeSeriesData["data"][metrics[i]]["schema"]["columnsToIndexMapping"]["baselineValue"];
        var indexOfCurrent = timeSeriesData["data"][metrics[i]]["schema"]["columnsToIndexMapping"]["currentValue"];
        var indexOfRatio = timeSeriesData["data"][metrics[i]]["schema"]["columnsToIndexMapping"]["ratio"];

        for (var t = 0, tlen = timeSeriesData["timeBuckets"].length; t < tlen; t++) {

            var baselineValue = timeSeriesData["data"][metrics[i]]["responseData"][t][0];
            var currentValue = timeSeriesData["data"][metrics[i]]["responseData"][t][1];
            var deltaPercentage = parseInt(timeSeriesData["data"][metrics[i]]["responseData"][t][2]);
            metricBaselineData.push(baselineValue);
            metricCurrentData.push(currentValue);
            deltaPercentageData.push(deltaPercentage);
        }
        lineChartData[metrics[i] + "-baseline"] = metricBaselineData;
        lineChartData[metrics[i] + "-current"] = metricCurrentData;
        barChartData[metrics[i] + "-delta"] = deltaPercentageData;

        colors[metrics[i] + "-baseline"] = colorArray[i];
        colors[metrics[i] + "-current"] = colorArray[i];
        colors[metrics[i] + "-delta"] = colorArray[i];

    }

    metricLineChart = c3.generate({
        bindto: lineChartPlaceholder,
        padding: {
            top: 0,
            right: 100,
            bottom: 20,
            left: 100
        },
        data: {
            x: 'time',
            json: lineChartData,
            type: 'spline',
            colors: colors
        },
        zoom: {
            enabled: true,
            rescale:true
        },
        axis: {
            x: {
                type: 'timeseries',
                tick: {
                    count:11,
                    format: dateTimeFormat
                }
            },
            y: {
                tick: {
                    //format integers with comma-grouping for thousands
                    format: d3.format(",.1 ")
                }
            }
        },
        //regions: regions,
        legend: {
            show: false
        },
        grid: {
            x: {
                show: false
            },
            y: {
                show: false
            }
        },
        point: {
            show: false
        }
    });


    var numAnomalies = anomalyData.length;
    var regionColors;

    if (anomalyData.length > 0 && anomalyData[0]["regionColor"]) {

        regionColors = [];
        regionColors.push( anomalyData[0]["regionColor"] )

    } else if(parseInt(numAnomalies) < 10) {
        regionColors = d3.scale.category10().range();

    } else if (numAnomalies < 20) {
        regionColors = d3.scale.category20().range();
    } else {
        regionColors = colorScale(numAnomalies);
    }

    //paint the anomaly regions based on anomaly id
    for (var i = 0; i < numAnomalies; i++) {
        var anomalyId = "anomaly-id-" + anomalyData[i]["id"];
        d3.select("." + anomalyId + " rect")
            .style("fill", regionColors[i])
    }

    var numAnomalies = anomalyData.length;
    var regionColors;

    if (anomalyData.length > 0 && anomalyData[0]["regionColor"]) {

        regionColors = [];
        regionColors.push( anomalyData[0]["regionColor"] )

    } else if(parseInt(numAnomalies) < 10) {
        regionColors = d3.scale.category10().range();

    } else if (numAnomalies < 20) {
        regionColors = d3.scale.category20().range();
    } else {
        regionColors = colorScale(numAnomalies);
    }


    //paint the anomaly regions based on anomaly id
    for (var i = 0; i < numAnomalies; i++) {
        var anomalyId = "anomaly-id-" + anomalyData[i]["id"];
        d3.select("." + anomalyId + " rect")
            .style("fill", regionColors[i])
    }

    metricChangeChart = c3.generate({

        bindto: barChartPlaceholder,
        padding: {
            top: 0,
            right: 100,
            bottom: 0,
            left: 100
        },
        data: {
            x: 'time',
            json: barChartData,
            type: 'area-spline',
            colors: colors
        },
        zoom: {
        enabled: true,
            rescale:true
        },
        axis: {
            x: {
                label: {
                    text: "Time"
                },
                type: 'timeseries',
                tick: {
                    count:11,
                    width:84,
                    multiline: true,
                    format: dateTimeFormat
                }
            },
            y: {
                label: {
                    text: "% change",
                    position: 'outer-middle'
                },
                tick: {
                    //count: 5,
                    format: function (d) {
                        return d.toFixed(2)
                    }
                }
            }
        },
        legend: {
            show: false
        },
        grid: {
            x: {

            },
            y: {
                lines: [
                    {
                        value: 0
                    }
                ]
            }
        },
        bar: {
            width: {
                ratio: .5
            }
        }
    });

    attach_MetricTimeSeries_EventListeners(currentView)

} //end of drawMetricTimeSeries


function drawAnomalyTimeSeries(timeSeriesData,anomalyData, tab, placeholder) {

     var currentView = $("#" + tab + "-display-chart-section");

     var aggTimeGranularity = (window.datasetConfig.dataGranularity) ? window.datasetConfig.dataGranularity : "HOURS";
     var dateTimeFormat = "%I:%M %p";
     if (aggTimeGranularity == "DAYS" ) {
         dateTimeFormat = "%m-%d";
     }else if(timeSeriesData.summary.baselineEnd - timeSeriesData.summary.baselineStart > 86400000 ){
         dateTimeFormat = "%m-%d %I %p";
     }

     var lineChartPlaceholder = $("#"+ placeholder, currentView)[0];
     // Metric(s)
     var metrics = timeSeriesData["metrics"];
     var lineChartData = {};
     var xTicksBaseline = [];
     var xTicksCurrent = [];
     var colors = {};
     var regions = [];

     for (var t = 0, len = timeSeriesData["timeBuckets"].length; t < len; t++) {
         var timeBucket = timeSeriesData["timeBuckets"][t]["currentStart"];
         var currentEnd = timeSeriesData["timeBuckets"][t]["currentEnd"];
         xTicksBaseline.push(timeBucket);
         xTicksCurrent.push(timeBucket);
     }

     for (var i = 0; i < anomalyData.length; i++) {
         var anomaly = anomalyData[i];

         var anomalyStart = anomaly.startTime;
         var anomalyEnd = anomaly.endTime;
         var anomayID = "anomaly-id-" + anomaly.id;
         regions.push({'axis': 'x', 'start': anomalyStart, 'end': anomalyEnd, 'class': 'regionX ' + anomayID });
     }
     lineChartData["time"] = xTicksCurrent;

     for (var i = 0, mlen = metrics.length; i < mlen; i++) {
         var baselineData = [];
         var currentData = [];

         var indexOfBaseline = timeSeriesData["data"][metrics[i]]["schema"]["columnsToIndexMapping"]["baselineValue"];
         var indexOfCurrent = timeSeriesData["data"][metrics[i]]["schema"]["columnsToIndexMapping"]["currentValue"];

         for (var t = 0, tlen = timeSeriesData["timeBuckets"].length; t < tlen; t++) {

             var baselineValue = timeSeriesData["data"][metrics[i]]["responseData"][t][indexOfBaseline];
             var currentValue = timeSeriesData["data"][metrics[i]]["responseData"][t][indexOfCurrent];
             baselineData.push(baselineValue);
             currentData.push(currentValue);
         }
         lineChartData["baseline"] = baselineData;
         lineChartData["current"] = currentData;

         colors["baseline"] = '#1f77b4';
         colors["current"] = '#ff5f0e';

     }

     var anomalyThumbnailLineChart = c3.generate({
         bindto: lineChartPlaceholder,
         padding: {
             top: 0,
             right: 100,
             bottom: 20,
             left: 100
         },
         data: {
             x: 'time',
             axes: {
                 baseline: 'y',
                 current: 'y2'
             },
             json: lineChartData,
             type: 'area-spline',
             colors: colors

         },
         zoom: {
             enabled: true,
            rescale:true
         },
         axis: {
             min:{
                 y2:0
             },

             x: {
                 type: 'timeseries',
                 tick: {

                     format: dateTimeFormat,
                     count:5
                 }
             },
             y: {
                 show: false,
                 min: 0
             },
             y2: {
                 show: true,

                 tick: {
                     //format integers with comma-grouping for thousands
                     format: d3.format(",.1r ")

                 },
                 min: 0
             }
         },
         regions: regions,
         legend: {
             show: false
         },
         grid: {
             x: {
                 show: false
             },
             y: {
                 show: true
             }
         },
         point: {
             show: false
         }
     });


    $(".c3-axis path.domain, .c3-axis-y2 line, .c3-axis-x line", lineChartPlaceholder).hide();
    $(".c3-axis-x path.domain", lineChartPlaceholder).hide();

     var numAnomalies = anomalyData.length;
     var regionColors = ['ff0000'];//'ff7f0e'



     //paint the anomaly regions based on anomaly id
     for (var i = 0; i < numAnomalies; i++) {
         var anomalyId = "anomaly-id-" + anomalyData[i]["id"];
         d3.select("." + anomalyId + " rect")
             .style("fill", regionColors[i])
     }

 } //end of drawAnomalyTimeSeries

function renderAnomalyThumbnails(data, tab) {
    //Error handling when data is falsy (empty, undefined or null)
    if (!data) {
        $("#" + tab + "-chart-area-error").empty()
        var warning = $('<div></div>', { class: 'uk-alert uk-alert-warning' })
        warning.append($('<p></p>', { html: 'Something went wrong. Please try and reload the page. Error: anomalies data =' + data  }))
        $("#" + tab + "-chart-area-error").append(warning)
        $("#" + tab + "-chart-area-error").show()
        return
    }

    /* Handelbars template for table */
    var result_anomalies_template = HandleBarsTemplates.template_anomalies(data);
    $("#" + tab + "-display-chart-section").append(result_anomalies_template);

    /** Create Datatables instance of the anomalies table **/
    $("#anomalies-table").DataTable();

    attach_AnomalyTable_EventListeners()

    for(var i = 0, numAnomalies = data.length; i < numAnomalies; i++) {
        requestLineChart(i);

    }

     function requestLineChart(i) {
         var placeholder = "d3charts-" + i;
         var exploreDimension = data[i]["function"]["exploreDimensions"];
         var effectedValue = data[i]["dimensions"];
         var fnFilters = parseProperties(data[i]["function"]["filters"], {arrayValues: true});
         var startTime = data[i]["startTime"];
         var endTime = data[i]["endTime"];
         var anomalyId = data[i]["id"]
         var baselineStart = moment(parseInt(hash.currentStart)).add(-7, 'days')
         var baselineEnd = moment(parseInt(hash.currentEnd)).add(-7, 'days')
         var aggTimeGranularity = calcAggregateGranularity(hash.currentStart,hash.currentEnd);

        var dataset = hash.dataset;
        var compareMode = "WoW";
        var currentStart = hash.currentStart;
        var currentEnd = hash.currentEnd;
        var metrics = hash.metrics;


         effectedValue = effectedValue.replace(/[,*\s]/g, "");
         if(effectedValue.length == 0){
             effectedValue = "ALL";
         }

         var filters = {};
         if(fnFilters){
             if(exploreDimension){
                 if(effectedValue !== 'ALL'){
                     filters = fnFilters;
                     filters[exploreDimension] = [effectedValue];
                 }
             }else{
                 filters = fnFilters;
             }
         }else{
             if(exploreDimension ){
                if(effectedValue !== 'ALL'){
                    filters[exploreDimension] = [effectedValue];
                }
             }
         };

        filters = encodeURIComponent(JSON.stringify(filters));
        var timeSeriesUrl = "/dashboard/data/tabular?dataset=" + dataset + "&compareMode=" + compareMode //
            + "&currentStart=" + currentStart + "&currentEnd=" + currentEnd  //
            + "&baselineStart=" + baselineStart + "&baselineEnd=" + baselineEnd   //
            + "&aggTimeGranularity=" + aggTimeGranularity + "&metrics=" + metrics  + "&filters=" + filters;
        var tab = hash.view;

        getDataCustomCallback(timeSeriesUrl,tab).done(function (timeSeriesData) {
            var anomalyRegionData = [];
            anomalyRegionData.push({startTime: parseInt(startTime), endTime: parseInt(endTime), id: anomalyId, regionColor: '#eedddd'});
            drawAnomalyTimeSeries(timeSeriesData, anomalyRegionData, tab, placeholder)
        })
     }

}

function attach_MetricTimeSeries_EventListeners(currentView){

     //Unbind previously attached eventlisteners
     currentView.off("click");
     currentView.off("change");

     // Clicking the checkbox of the timeseries legend will redraw the timeseries
     // with the selected elements
     currentView.on("click", '.time-series-metric-checkbox', function () {
         anomalyTimeSeriesCheckbox(this);
     });

     //Select all / deselect all metrics option
     currentView.on("click", ".time-series-metric-select-all-checkbox", function () {
         anomalyTimeSelectAllCheckbox(this);
     });

 //Select all / deselect all metrics option
     currentView.on("click", "#self-service-link", function () {
        $(".header-tab[rel='self-service']").click();
     });

     function anomalyTimeSeriesCheckbox(target) {
         var checkbox = target;
         var checkboxObj = $(checkbox);
         metricName = checkboxObj.val();
         if (checkboxObj.is(':checked')) {
             //Show metric's lines on timeseries
             metricLineChart.show(metricName + "-current");
             metricLineChart.show(metricName + "-baseline");
             metricChangeChart.show(metricName + "-delta");

         } else {
             //Hide metric's lines on timeseries
             metricLineChart.hide(metricName + "-current");
             metricLineChart.hide(metricName + "-baseline");
             metricChangeChart.hide(metricName + "-delta");
         }
     }

     function anomalyTimeSelectAllCheckbox(target) {
         //if select all is checked
         if ($(target).is(':checked')) {
             //trigger click on each unchecked checkbox
             $(".time-series-metric-checkbox", currentView).each(function (index, checkbox) {
                 if (!$(checkbox).is(':checked')) {
                     $(checkbox).click();
                 }
             })
         } else {
             //trigger click on each checked checkbox
             $(".time-series-metric-checkbox", currentView).each(function (index, checkbox) {
                 if ($(checkbox).is(':checked')) {
                     $(checkbox).click();
                 }
             })
         }
     }

     //Set initial state of view

     //Show the first line on the timeseries
     var firstLegendLabel = $($(".time-series-metric-checkbox")[0])
     if( !firstLegendLabel.is(':checked')) {
         firstLegendLabel.click();
     }
 }

function attach_AnomalyTable_EventListeners(){

     //Unbind previously attached eventlisteners
     $("#anomalies-table").off("click");
     $("#anomalies-table").off("hide.uk.dropdown");
     $("#anomaly-result-thumbnails").off("click");
     $("#anomaly-result-thumbnails").off("hide.uk.dropdown");

     //Clicking a checkbox in the table toggles the region of that timerange on the timeseries chart
     $("#anomalies-table").on("change", ".anomaly-table-radio-label input", function () {
         chartForSingleAnomaly(this, "linechart-placeholder");
     });

     //Clicking a checkbox in the table takes user to related heatmap chart
     $("#anomalies-table").on("click", ".heatmap-link", function () {
         showHeatMapOfAnomaly(this);
     });

     //Clicking the feedback option will trigger the ajax - post
     $("#anomalies-table").on("click", ".feedback-list", function () {
         $(this).next("textarea").show();
     });


     $('.feedback-dropdown[data-uk-dropdown]').on('hide.uk.dropdown', function(){
         submitAnomalyFeedback(this);
     });

    //Clicking a checkbox in the table takes user to related heatmap chart
    $("#anomaly-result-thumbnails").on("click", ".heatmap-link", function () {
        showHeatMapOfAnomaly(this);
    });

    //Clicking the feedback option will trigger the ajax - post
    $("#anomaly-result-thumbnails").on("click", ".feedback-list", function () {
        $(this).next("textarea").show();
    });

     /** Compare/Tabular view and dashboard view heat-map-cell click switches the view to compare/heat-map
      * focusing on the timerange of the cell or in case of cumulative values it query the cumulative timerange **/
     function showHeatMapOfAnomaly(target) {

         var $target = $(target);
         hash.view = "compare";
         hash.aggTimeGranularity = "aggregateAll";

         var currentStartUTC = $target.attr("data-start-utc-millis");
         var currentEndUTC = $target.attr("data-end-utc-millis");

         //Using WoW for anomaly baseline
         var baselineStartUTC = moment(parseInt(currentStartUTC)).add(-7, 'days').valueOf();
         var baselineEndUTC = moment(parseInt(currentEndUTC)).add(-7, 'days').valueOf();

         hash.baselineStart = baselineStartUTC;
         hash.baselineEnd = baselineEndUTC;
         hash.currentStart = currentStartUTC;
         hash.currentEnd = currentEndUTC;
         delete hash.dashboard;
         metrics = [];
         var metricName = $target.attr("data-metric");
         metrics.push(metricName);
         hash.metrics = metrics.toString();

         //update hash will trigger window.onhashchange event:
         // update the form area and trigger the ajax call
         window.location.hash = encodeHashParameters(hash);
     }

     function submitAnomalyFeedback(target) {
         var $target = $(target);
         var selector = $(".selected-feedback", $target);
         var feedbackType = getFeedbackTypeFromString(selector.attr("value"));
         var anomalyId = selector.attr("data-anomaly-id");
         var comment = $(".feedback-comment", $target).val();

         //Remove control characters
         comment = comment.replace(/[\x00-\x1F\x7F-\x9F]/g, "")
         if(feedbackType){

             var data = '{ "feedbackType": "' + feedbackType + '","comment": "'+ comment +'"}';
             var url = "/dashboard/anomaly-merged-result/feedback/" + anomalyId;

             //post anomaly feedback
             submitData(url, data).done(function () {
                 $(selector).addClass("green-background");
             }).fail(function(){
                 $(selector).addClass("red-background");
             })
         }
     }

     function chartForSingleAnomaly(target, placeholder) {

         var button = $(target);
         var dimension = button.attr("data-explore-dimensions");
         var value = button.attr("data-dimension-value");
         var startTime = button.attr("data-start-time");
         var endTime = button.attr("data-end-time");
         var anomalyId = button.attr("data-anomaly-id");
         var row = button.closest('tr')
         var colorRGB = $(".color-box", row).css("background-color");
         var colorHEX = rgbToHex(colorRGB);
         var baselineStart = moment(parseInt(hash.currentStart)).add(-7, 'days')
         var baselineEnd = moment(parseInt(hash.currentEnd)).add(-7, 'days')
         var aggTimeGranularity = (window.datasetConfig.dataGranularity) ? window.datasetConfig.dataGranularity : "HOURS";
         var dataset = hash.dataset;
         var compareMode = "WoW";
         var currentStart = hash.currentStart;
         var currentEnd = hash.currentEnd;
         var metrics = hash.metrics;

         var filter = "{}";
         if(dimension && value && value != 'ALL') {
             filter = '{"'+dimension+'":["'+value+'"]}';
         }

         var timeSeriesUrl = "/dashboard/data/tabular?dataset=" + dataset + "&compareMode=" + compareMode //
             + "&currentStart=" + currentStart + "&currentEnd=" + currentEnd  //
             + "&baselineStart=" + baselineStart + "&baselineEnd=" + baselineEnd   //
             + "&aggTimeGranularity=" + aggTimeGranularity + "&metrics=" + metrics+ "&filters=" + filter;
         var tab = hash.view;

         getDataCustomCallback(timeSeriesUrl,tab ).done(function (timeSeriesData) {
             //Error handling when data is falsy (empty, undefined or null)
             if (!timeSeriesData) {
                 // do nothing
                 return
             } else {
                 $("#" + tab + "-chart-area-error").hide();
             }
             var anomalyRegionData = [];
             anomalyRegionData.push({startTime: parseInt(startTime), endTime: parseInt(endTime), id: anomalyId, regionColor: colorHEX});
             drawMetricTimeSeries(timeSeriesData, anomalyRegionData, tab, placeholder);

         });
     }

    //Set initial state of view
    $(".box-inner .change").each( function(){
        var change = $(this).text();
        change = parseFloat(change.trim())
        if( change < 0){
            $(this).addClass("negative-icon");

        }else if( change > 0){
            $(this).addClass("positive-icon");
        }
    });

 }

function tipToUser(tab) {
    var tipToUser = document.createElement("div");
    tipToUser.id = "anomaly-metric-tip";
    tipToUser.className = "tip-to-user uk-alert uk-form-row";
    tipToUser.setAttribute("data-tip-source", "not-anomaly-metric");
    var closeIcon = document.createElement("i");
    closeIcon.className = "close-parent uk-icon-close";
    var msg = document.createElement("p");
    msg.innerText = "There is no anomaly monitoring function set up for this metric. You can create a function on the"
    var link = document.createElement("a");
    link.id = "self-service-link";
    link.innerText = "Self Service tab.";

    msg.appendChild(link);
    tipToUser.appendChild(closeIcon);
    tipToUser.appendChild(msg);
    $("#" + tab + "-display-chart-section").append(tipToUser)
}























