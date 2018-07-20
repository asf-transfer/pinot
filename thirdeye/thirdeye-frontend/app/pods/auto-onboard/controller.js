import {observer, computed, set, get} from '@ember/object';
import {later, debounce} from '@ember/runloop';
import {reads, gt, or} from '@ember/object/computed';
import Controller from '@ember/controller';
import {
  filterObject,
  filterPrefix,
  hasPrefix,
  toBaselineUrn,
  toBaselineRange,
  toCurrentUrn,
  toOffsetUrn,
  toFilters,
  toFilterMap,
  appendFilters,
  dateFormatFull,
  colorMapping
} from 'thirdeye-frontend/utils/rca-utils';
import {checkStatus} from 'thirdeye-frontend/utils/utils';
import fetch from 'fetch';

export default Controller.extend({
  detectionConfig: null,

  detectionConfigName: null,

  datasetName: null,

  dimensions: null,

  selectedDimensions: '{}',

  selectedFilters: '{}',

  selectedMetric: null,

  checkboxDefault: true,

  filterOptions: {},

  metrics: null,

  metricsProperties: {},

  detectionConfigCron: null,

  metricUrn: null,

  baseline: null,

  filterMap: null,

  toggleChecked: false,

  output: null,

  idToNames: {},

  queryParams: ['detectionId'],

  hasDetectionId: observer('detectionId', function () {
    const detectionId = this.get('detectionId');
    fetch(`/dataset-auto-onboard/` + detectionId)
      .then(res => res.json())
      .then(res => {
        const nestedProperties = res['properties']['nested'];
        // fill in values:
        this.set('detectionConfigName', res['name']);
        this.set('topk', nestedProperties[0]['k']);
        this.set('minvalue', nestedProperties[0]['minvalue']);
        this.set('mincontribution', nestedProperties[0]['minContribution']);
        this.set('datasetName', res['properties']['datasetName']);

        this._datasetNameChanged().then(res => {

          let dimensionBreakdownUrn = null;
          const idToNames = this.get('idToNames');
          const metricsProperties = get(this, 'metricsProperties');

          // fill in dimensions
          this.set('selectedDimensions', JSON.stringify({
            'dimensions': nestedProperties[0]['dimensions']
          }));

          // fill in filters
          let urnPieces = nestedProperties[0]['metricUrn'].split(':');
          const filters = {};
          var i;
          for(i = 3; i < urnPieces.length; i++) {
            const filter = urnPieces[i].split('=');
            if (filter[0] in filters) {
              filters[filter[0]].push(filter[1]);
            } else {
              filters[filter[0]] = [filter[1]];
            }
          }
          this.set('selectedFilters', JSON.stringify(filters));

          // fill in selected metrics
          const metricIds = nestedProperties.reduce(function (obj, property) {
            let urn;
            if ('nestedMetricUrn' in property) {
              urn = property['nestedMetricUrn'];
              dimensionBreakdownUrn = property['metricUrn'];
            } else {
              urn = property['metricUrn'];
            }
            obj.push(urn.split(':')[2]);
            return obj;
          }, []);

          Object.keys(metricsProperties).forEach(function (key) {
            console.log(metricsProperties[key]['id']);
            console.log(metricIds);
            if(metricIds.indexOf(metricsProperties[key]['id'].toString()) == -1) {
              set(metricsProperties[key], 'monitor', false);
            }
          });

          // fill in dimension breakdown metric
          this.set('selectedMetric', idToNames[this._metricUrnToId(dimensionBreakdownUrn)]);
        });
      })
      .catch(error => console.error(`Fetch Error =\n`, error))
  }),

  generalFieldsEnabled: computed.or('dimensions'),

  metricsFieldEnabled: computed.or('metrics'),

  _writeDetectionConfig(detectionConfigBean) {
    const jsonString = JSON.stringify(detectionConfigBean);

    return fetch(`/thirdeye/entity?entityType=DETECTION_CONFIG`, {method: 'POST', body: jsonString})
      .then(checkStatus)
      .then(res => set(this, 'output', `saved '${detectionConfigBean.name}' as id ${res}`))
      .catch(err => set(this, 'output', err));
  },

  _metricUrnToId(metricUrn) {
    // return metricUrn.substring(16, metricUrn.indexOf(':', 16));
    return metricUrn.split(':')[2];
  },

  _datasetNameChanged() {
    const url = `/dataset-auto-onboard/metrics?dataset=` + get(this, 'datasetName');
    return fetch(url)
      .then(res => res.json())
      .then(res => this.set('metrics', res))
      .then(res => this.set('metricsProperties', res.reduce(function (obj, metric) {
        obj[metric["name"]] = {
          "id": metric['id'], "urn": "thirdeye:metric:" + metric['id'], "monitor": true
        };
        return obj;
      }, {})))
      .then(res => {
        this.set('metrics', Object.keys(res));
        const metricUrn = res[Object.keys(res)[0]]['id'];
        const idToNames = {};
        Object.keys(res).forEach(function (key) {
          idToNames[res[key]['id']] = key;
        });
        this.set('idToNames', idToNames);
        this.set('metricUrn', metricUrn);
        fetch(`/data/autocomplete/filters/metric/${metricUrn}`)
          .then(checkStatus)
          .then(res => {
            this.set('filterOptions', res);
            this.set('dimensions', {dimensions: Object.keys(res)});
          })
      })
      .catch(error => console.error(`Fetch Error =\n`, error));
  },

  actions: {
    onSave() {
      this._datasetNameChanged();
    },

    toggleCheckBox(name) {
      const metricsProperties = get(this, 'metricsProperties');
      set(metricsProperties[name], 'monitor', !metricsProperties[name]['monitor']);
    },

    onChangeValue(property, value) {
      const metricsProperties = get(this, 'metricsProperties');
      this.set(property, value);
      Object.keys(metricsProperties).forEach(function (key) {
        metricsProperties[key][property] = value;
      });
    },

    onFilters(filters) {
      this.set('selectedFilters', filters);
      const metricsProperties = get(this, 'metricsProperties');
      const filterMap = JSON.parse(filters);
      Object.keys(metricsProperties).forEach(function (key) {
        const metricProperty = metricsProperties[key];
        let metricUrn = "thirdeye:metric:" + metricProperty['id'];
        Object.keys(filterMap).forEach(function (key) {
          filterMap[key].forEach(function (value) {
            metricUrn = metricUrn + ":" + key + "=" + value;
          })
        });
        metricsProperties[key]['urn'] = metricUrn;
      });
    },

    onSubmit() {
      const metricsProperties = get(this, 'metricsProperties');
      const nestedProperties = [];
      const selectedMetric = this.get('selectedMetric');
      const selectedDimensions = this.get('selectedDimensions');
      Object.keys(metricsProperties).forEach(function (key) {
        const properties = metricsProperties[key];
        if (!properties['monitor']) {
          return;
        }
        const detectionConfig = {
          className: "com.linkedin.thirdeye.detection.algorithm.DimensionWrapper", nested: [{
            className: "com.linkedin.thirdeye.detection.algorithm.MovingWindowAlgorithm",
            baselineWeeks: 4,
            windowSize: "4 weeks",
            changeDuration: "7d",
            outlierDuration: "12h",
            aucMin: -10,
            zscoreMin: -4,
            zscoreMax: 4
          }]
        };
        if (selectedMetric == null) {
          detectionConfig['metricUrn'] = properties['urn'];
        } else {
          detectionConfig['metricUrn'] = metricsProperties[selectedMetric]['urn'];
          detectionConfig['nestedMetricUrn'] = properties['urn'];
        }

        if (selectedDimensions != null) {
          detectionConfig['dimensions'] = selectedDimensions['dimensions'];
        }
        if (properties['topk']) {
          detectionConfig['k'] = parseInt(properties['topk']);
        }
        if (properties['minvalue']) {
          detectionConfig['minValue'] = parseFloat(properties['minvalue']);
        }
        if (properties['mincontribution']) {
          detectionConfig['minContribution'] = parseFloat(properties['mincontribution']);
        }
        nestedProperties.push(detectionConfig);
      });

      const configResult = {
        "cron": "45 10/15 * * * ? *", "name": get(this, 'detectionConfigName'), "lastTimestamp": 0, "properties": {
          "className": "com.linkedin.thirdeye.detection.algorithm.MergeWrapper",
          "maxGap": 7200000,
          "nested": nestedProperties,
          "datasetName": get(this, 'datasetName')
        }
      };

      this._writeDetectionConfig(configResult);
    },

    onSelectDimension(dims) {
      this.set('selectedDimensions', dims);
    },

    onChangeName(name) {
      this.set('detectionConfigName', name);
    },

    onSelectMetric(name) {
      this.set('selectedMetric', name);
    }
  }
});
