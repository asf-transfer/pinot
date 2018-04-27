import Service from '@ember/service';
import { assert } from '@ember/debug';
import { isPresent } from "@ember/utils";

export default Service.extend({
  durationObj: null,
  durationTypes: {
    duration: 'string',
    startDate: 'number',
    endDate: 'number'
  },

  init() {
    this._super(...arguments);
    this.set('durationObj', {});
  },

  /**
   * Saves new time range settings to persist
   * @method setDuration
   * @param {Object} newDuration - new incoming time range object
   * @return {undefined}
   */
  setDuration(newDuration) {
    const propsObj = this.get('durationTypes');
    const requiredKeys = Object.keys(propsObj);

    requiredKeys.forEach((key) => {
      assert(`you must pass ${key} param as ${propsObj[key]}.`, typeof newDuration[key] === propsObj[key]);
    });

    this.set('durationObj', newDuration);
  },

  /**
   * Decides which time range to load as default (query params, default set, or locally cached)
   * @method getDuration
   * @param {Object} queryParams - range-related properties in querystring
   * @param {Object} defaultDurationObj - basic default time range setting
   * @return {Object}
   */
  getDuration(queryParams, defaultDurationObj) {
    const cachedDuration = this.get('durationObj');
    const isDurationCached = Object.keys(cachedDuration).length > 0;
    // Check for presence of each time range key in qeury params
    const isDurationInQuery = isPresent(queryParams.duration) && isPresent(queryParams.startDate) && isPresent(queryParams.endDate);
    // Use querystring time range if present. Else, use preset defaults
    const defaultDuration = isDurationInQuery ? queryParams : defaultDurationObj;
    // Prefer cached time range if present. Else, load from defaults
    const newDurationObj = isDurationCached ? cachedDuration : defaultDuration;
    // If no time range is cached for the session, cache the new one
    if (!isDurationCached) {
      this.set('durationObj', newDurationObj);
    }
    return newDurationObj;
  },

  /**
   * Clears the cached duration object (reset)
   * @method removeDuration
   * @return {undefined}
   */
  removeDuration() {
    this.set('durationObj', {});
  }
});
