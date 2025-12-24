/**
 * Leaflet Adapter for Google Maps Migration
 * 
 * This adapter provides a compatibility layer to ease migration from Google Maps to Leaflet/OSM.
 * It wraps Leaflet functionality to mimic some Google Maps API patterns.
 */

console.log('Leaflet Adapter v3.7 loaded - Smart viewport-based world wrapping (performance optimized)');

// Geometry helper functions (replaces google.maps.geometry.spherical)
const LeafletGeometry = {
    /**
     * Calculate spherical distance between two points
     * @param {Object} from - {lat, lng}
     * @param {Object} to - {lat, lng}
     * @returns {number} Distance in meters
     */
    computeDistanceBetween: function(from, to) {
        const R = 6371e3; // Earth's radius in meters
        const φ1 = from.lat * Math.PI / 180;
        const φ2 = to.lat * Math.PI / 180;
        const Δφ = (to.lat - from.lat) * Math.PI / 180;
        const Δλ = (to.lng - from.lng) * Math.PI / 180;

        const a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
                Math.cos(φ1) * Math.cos(φ2) *
                Math.sin(Δλ/2) * Math.sin(Δλ/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    },

    /**
     * Interpolate between two points (for animation)
     * Replicates google.maps.geometry.spherical.interpolate
     * @param {Object} from - {lat, lng}
     * @param {Object} to - {lat, lng}
     * @param {number} fraction - 0 to 1
     * @returns {Object} {lat, lng}
     */
    interpolate: function(from, to, fraction) {
        const lat1 = from.lat * Math.PI / 180;
        const lon1 = from.lng * Math.PI / 180;
        const lat2 = to.lat * Math.PI / 180;
        const lon2 = to.lng * Math.PI / 180;

        const d = 2 * Math.asin(Math.sqrt(
            Math.pow(Math.sin((lat1 - lat2) / 2), 2) +
            Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lon1 - lon2) / 2), 2)
        ));

        if (d === 0) {
            return { lat: from.lat, lng: from.lng };
        }

        const A = Math.sin((1 - fraction) * d) / Math.sin(d);
        const B = Math.sin(fraction * d) / Math.sin(d);

        const x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2);
        const y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2);
        const z = A * Math.sin(lat1) + B * Math.sin(lat2);

        const lat = Math.atan2(z, Math.sqrt(x * x + y * y));
        const lon = Math.atan2(y, x);

        return {
            lat: lat * 180 / Math.PI,
            lng: lon * 180 / Math.PI
        };
    },

    /**
     * Compute heading between two points
     * @param {Object} from - {lat, lng}
     * @param {Object} to - {lat, lng}
     * @returns {number} Heading in degrees (0-360)
     */
    computeHeading: function(from, to) {
        const lat1 = from.lat * Math.PI / 180;
        const lat2 = to.lat * Math.PI / 180;
        const dLng = (to.lng - from.lng) * Math.PI / 180;

        const y = Math.sin(dLng) * Math.cos(lat2);
        const x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        const bearing = Math.atan2(y, x);

        return (bearing * 180 / Math.PI + 360) % 360;
    }
};

/**
 * Fix paths that cross the antimeridian (180° longitude)
 * This ensures Leaflet draws the shorter path instead of going around the world
 * @param {Array} latlngs - Array of [lat, lng] coordinates
 * @returns {Array} Adjusted coordinates
 */
function fixAntimeridianPath(latlngs) {
    if (!latlngs || latlngs.length < 2) {
        return latlngs;
    }
    
    var result = [[latlngs[0][0], latlngs[0][1]]];
    var offset = 0;
    
    for (var i = 1; i < latlngs.length; i++) {
        var prevLng = latlngs[i - 1][1] + offset;
        var currLng = latlngs[i][1];
        
        // Calculate the difference
        var diff = currLng - (latlngs[i - 1][1]);
        
        // If the difference is greater than 180°, the line crosses the antimeridian
        // Adjust the longitude to continue in the same direction
        if (diff > 180) {
            offset -= 360;
        } else if (diff < -180) {
            offset += 360;
        }
        
        result.push([latlngs[i][0], currLng + offset]);
    }
    
    return result;
}

// Enhanced control array for Google Maps compatibility
function createControlArray() {
    var arr = [];
    arr.getLength = function() {
        return this.length;
    };
    arr.clear = function() {
        this.splice(0, this.length);
    };
    arr.push = function(element) {
        Array.prototype.push.call(this, element);
    };
    return arr;
}

// Add controls property to Leaflet Map prototype
var originalMapInitialize = L.Map.prototype.initialize;
L.Map.prototype.initialize = function(id, options) {
    originalMapInitialize.call(this, id, options);
    
    // Create Google Maps compatible controls structure
    if (!this.controls) {
        this.controls = {};
        // Initialize control positions with numeric keys (Google Maps style)
        // Position values: TOP_LEFT=0, TOP_CENTER=1, TOP_RIGHT=2, LEFT_TOP=3, LEFT_CENTER=4, LEFT_BOTTOM=5,
        //                 RIGHT_TOP=6, RIGHT_CENTER=7, RIGHT_BOTTOM=8, BOTTOM_LEFT=9, BOTTOM_CENTER=10, BOTTOM_RIGHT=11
        for (var i = 0; i < 12; i++) {
            this.controls[i] = createControlArray();
        }
        
        // Also add string keys for convenience
        this.controls['TOP_LEFT'] = this.controls[0];
        this.controls['TOP_CENTER'] = this.controls[1];
        this.controls['TOP_RIGHT'] = this.controls[2];
        this.controls['LEFT_TOP'] = this.controls[3];
        this.controls['LEFT_CENTER'] = this.controls[4];
        this.controls['LEFT_BOTTOM'] = this.controls[5];
        this.controls['RIGHT_TOP'] = this.controls[6];
        this.controls['RIGHT_CENTER'] = this.controls[7];
        this.controls['RIGHT_BOTTOM'] = this.controls[8];
        this.controls['BOTTOM_LEFT'] = this.controls[9];
        this.controls['BOTTOM_CENTER'] = this.controls[10];
        this.controls['BOTTOM_RIGHT'] = this.controls[11];
    }
};

// Enhanced Marker class
L.EnhancedMarker = L.Marker.extend({
    initialize: function(latlng, options) {
        L.Marker.prototype.initialize.call(this, latlng, options);
        this._visible = true;
        this._customData = options.customData || {};
    },

    setVisible: function(visible) {
        this._visible = visible;
        if (visible) {
            this.setOpacity(1);
        } else {
            this.setOpacity(0);
        }
    },

    getVisible: function() {
        return this._visible;
    },

    setTitle: function(title) {
        this.options.title = title;
        if (this._icon) {
            this._icon.title = title;
        }
    },

    getTitle: function() {
        return this.options.title;
    },

    getData: function(key) {
        return this._customData[key];
    },

    setData: function(key, value) {
        this._customData[key] = value;
    }
});

// Factory function for enhanced markers
L.enhancedMarker = function(latlng, options) {
    return new L.EnhancedMarker(latlng, options);
};

// Enhanced Polyline class
L.EnhancedPolyline = L.Polyline.extend({
    initialize: function(latlngs, options) {
        L.Polyline.prototype.initialize.call(this, latlngs, options);
        this._customData = options.customData || {};
    },

    getData: function(key) {
        return this._customData[key];
    },

    setData: function(key, value) {
        this._customData[key] = value;
    },

    // Add shadow polyline for better visibility
    addShadow: function(map, shadowOptions) {
        const shadowOpts = L.extend({}, this.options, shadowOptions || {
            color: '#000000',
            weight: (this.options.weight || 2) + 2,
            opacity: 0.3
        });
        
        this._shadow = L.polyline(this.getLatLngs(), shadowOpts).addTo(map);
        return this._shadow;
    },

    removeShadow: function() {
        if (this._shadow && this._shadow._map) {
            this._shadow.remove();
        }
    }
});

// Factory function for enhanced polylines
L.enhancedPolyline = function(latlngs, options) {
    return new L.EnhancedPolyline(latlngs, options);
};

// Custom control position helper
const ControlPositions = {
    TOP_LEFT: 'topleft',
    TOP_CENTER: 'topcenter',
    TOP_RIGHT: 'topright',
    LEFT_TOP: 'topleft',
    LEFT_CENTER: 'leftcenter',
    LEFT_BOTTOM: 'bottomleft',
    RIGHT_TOP: 'topright',
    RIGHT_CENTER: 'rightcenter',
    RIGHT_BOTTOM: 'bottomright',
    BOTTOM_LEFT: 'bottomleft',
    BOTTOM_CENTER: 'bottomcenter',
    BOTTOM_RIGHT: 'bottomright'
};

// Add center positions that Leaflet doesn't have by default
L.Control.TopCenter = L.Control.extend({
    options: {
        position: 'topleft'
    },
    onAdd: function(map) {
        const container = L.DomUtil.create('div', 'leaflet-top-center leaflet-bar');
        container.style.cssText = 'position: absolute; left: 50%; transform: translateX(-50%); margin-top: 10px;';
        return container;
    }
});

L.control.topCenter = function(opts) {
    return new L.Control.TopCenter(opts);
};

// Map controls manager (mimics Google Maps controls array)
class MapControls {
    constructor(map, position) {
        this.map = map;
        this.position = position;
        this.controls = [];
        this.container = null;
    }

    push(element) {
        this.controls.push(element);
        if (!this.container) {
            this.createContainer();
        }
        this.container.appendChild(element);
    }

    insertAt(index, element) {
        if (index >= this.controls.length) {
            this.push(element);
        } else {
            this.controls.splice(index, 0, element);
            if (!this.container) {
                this.createContainer();
            }
            const refChild = this.container.children[index];
            this.container.insertBefore(element, refChild);
        }
    }

    clear() {
        if (this.container) {
            this.container.innerHTML = '';
        }
        this.controls = [];
    }

    getLength() {
        return this.controls.length;
    }

    createContainer() {
        const CustomControl = L.Control.extend({
            onAdd: () => {
                const container = L.DomUtil.create('div', 'leaflet-control-custom');
                L.DomEvent.disableClickPropagation(container);
                return container;
            }
        });

        const control = new CustomControl({ position: this.position });
        control.addTo(this.map);
        this.container = control.getContainer();
    }
}

// Enhanced Map wrapper
L.EnhancedMap = L.Map.extend({
    initialize: function(id, options) {
        // Convert Google Maps options to Leaflet options
        const leafletOptions = {
            center: options.center ? [options.center.lat, options.center.lng] : [0, 0],
            zoom: options.zoom || 2,
            minZoom: options.minZoom || 2,
            maxZoom: options.maxZoom || 18,
            zoomControl: options.zoomControl !== false,
            worldCopyJump: true
        };

        if (options.restriction) {
            const bounds = options.restriction.latLngBounds;
            leafletOptions.maxBounds = [
                [bounds.south, bounds.west],
                [bounds.north, bounds.east]
            ];
        }

        L.Map.prototype.initialize.call(this, id, leafletOptions);

        // Add default Thunderforest Outdoors tiles
        const tileOptions = options.tileLayer || {
            url: 'https://{s}.tile.thunderforest.com/outdoors/{z}/{x}/{y}{r}.png?apikey={apikey}',
            attribution: '&copy; <a href="http://www.thunderforest.com/">Thunderforest</a>, &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            apikey: '<your apikey>',
            maxZoom: 22
        };

        this.baseLayer = L.tileLayer(tileOptions.url, tileOptions).addTo(this);

        // Add alternative tile layers for map type switching
        this.tileLayers = {
            standard: this.baseLayer,
            light: L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
            }),
            dark: L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
            }),
            satellite: L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                attribution: 'Tiles &copy; Esri'
            })
        };

        // Initialize control positions
        this.controls = {};
        Object.keys(ControlPositions).forEach(key => {
            const position = ControlPositions[key];
            this.controls[key] = new MapControls(this, position);
        });

        this._currentMapType = 'standard';
        this._gestureHandling = options.gestureHandling || 'greedy';

        // Apply gesture handling
        if (this._gestureHandling === 'greedy') {
            this.scrollWheelZoom.enable();
            this.dragging.enable();
        }
    },

    setMapTypeId: function(mapType) {
        if (this._currentMapType === mapType) return;

        // Remove current layer
        if (this.baseLayer) {
            this.removeLayer(this.baseLayer);
        }

        // Add new layer
        this.baseLayer = this.tileLayers[mapType] || this.tileLayers.standard;
        this.addLayer(this.baseLayer);
        this._currentMapType = mapType;

        this.fire('maptypeid_changed', { mapType: mapType });
    },

    getMapTypeId: function() {
        return this._currentMapType;
    },

    // Compatibility methods
    getBounds: function() {
        const bounds = L.Map.prototype.getBounds.call(this);
        return {
            getNorthEast: () => ({ lat: bounds.getNorth(), lng: bounds.getEast() }),
            getSouthWest: () => ({ lat: bounds.getSouth(), lng: bounds.getWest() }),
            contains: (latlng) => bounds.contains([latlng.lat, latlng.lng])
        };
    }
});

// Factory function for enhanced map
L.enhancedMap = function(id, options) {
    return new L.EnhancedMap(id, options);
};

// Utility: Convert Google Maps LatLng to Leaflet format
function convertLatLng(googleLatLng) {
    if (googleLatLng.lat && googleLatLng.lng) {
        return [googleLatLng.lat, googleLatLng.lng];
    }
    return googleLatLng;
}

// Utility: Create arrow decorator for polylines
function addArrowDecorator(polyline, color) {
    // Requires Leaflet.PolylineDecorator plugin
    if (typeof L.polylineDecorator !== 'undefined') {
        return L.polylineDecorator(polyline, {
            patterns: [
                {
                    offset: '100%',
                    repeat: 0,
                    symbol: L.Symbol.arrowHead({
                        pixelSize: 12,
                        polygon: false,
                        pathOptions: {
                            stroke: true,
                            color: color,
                            weight: 2
                        }
                    })
                }
            ]
        });
    }
    return null;
}

/**
 * Google Maps Compatibility Layer
 * Creates a partial google.maps object for backward compatibility
 */
if (typeof google === 'undefined') {
    window.google = {};
}

if (typeof google.maps === 'undefined') {
    google.maps = {
        // Marker constructor wrapper
        Marker: function(options) {
            var marker;
            
            if (options.position) {
                var latlng = [options.position.lat, options.position.lng];
                
                var markerOptions = {
                    title: options.title || '',
                    // Boost opacity slightly for better visibility (multiply by 1.2, cap at 1.0)
                    opacity: options.opacity !== undefined ? Math.min(options.opacity * 1.3, 1.0) : 1.0
                };
                
                // Handle custom icon
                if (options.icon) {
                    if (typeof options.icon === 'string') {
                        // For simple URL strings, use appropriate icon size for airport markers
                        markerOptions.icon = L.icon({
                            iconUrl: options.icon,
                            iconSize: [28, 28],
                            iconAnchor: [14, 14],
                            popupAnchor: [0, -14]
                        });
                    } else if (options.icon.url) {
                        markerOptions.icon = L.icon({
                            iconUrl: options.icon.url,
                            iconSize: options.icon.scaledSize ? 
                                [options.icon.scaledSize.width, options.icon.scaledSize.height] : 
                                [28, 28],
                            iconAnchor: options.icon.anchor ? 
                                [options.icon.anchor.x, options.icon.anchor.y] : 
                                [14, 14],
                            popupAnchor: [0, -14]
                        });
                    }
                }
                
                marker = L.marker(latlng, markerOptions);
                
                // Add compatibility methods
                marker.setVisible = function(visible) {
                    if (visible) {
                        if (options.map && !marker._map) {
                            marker.addTo(options.map);
                        }
                    } else {
                        if (marker._map) {
                            marker.remove();
                        }
                    }
                };
                
                marker.setMap = function(map) {
                    if (map) {
                        marker.addTo(map);
                    } else {
                        marker.remove();
                    }
                };
                
                marker.getPosition = function() {
                    var latlng = marker.getLatLng();
                    return { lat: latlng.lat, lng: latlng.lng };
                };
                
                // Store original Leaflet methods before overriding
                var leafletSetOpacity = marker.setOpacity.bind(marker);
                var leafletSetIcon = marker.setIcon.bind(marker);
                
                marker.setZIndex = function(zIndex) {
                    if (marker._icon) {
                        marker._icon.style.zIndex = zIndex;
                    }
                    marker._zIndex = zIndex;
                    return marker;
                };
                
                marker.setOpacity = function(opacity) {
                    leafletSetOpacity(opacity);
                    return marker;
                };
                
                marker.getOpacity = function() {
                    return marker.options.opacity;
                };
                
                marker.setIcon = function(icon) {
                    // Handle both string URLs and Google Maps icon objects
                    var leafletIcon;
                    if (typeof icon === 'string') {
                        leafletIcon = L.icon({
                            iconUrl: icon,
                            iconSize: [28, 28],
                            iconAnchor: [14, 14],
                            popupAnchor: [0, -14]
                        });
                    } else if (icon && icon.url) {
                        leafletIcon = L.icon({
                            iconUrl: icon.url,
                            iconSize: icon.scaledSize ? 
                                [icon.scaledSize.width, icon.scaledSize.height] : 
                                [28, 28],
                            iconAnchor: icon.anchor ? 
                                [icon.anchor.x, icon.anchor.y] : 
                                [14, 14],
                            popupAnchor: [0, -14]
                        });
                    } else if (icon && icon.options && icon.options.iconUrl) {
                        // Already a Leaflet icon, use as-is
                        leafletIcon = icon;
                    } else {
                        // Fallback to default icon
                        leafletIcon = new L.Icon.Default();
                    }
                    leafletSetIcon(leafletIcon);
                    return marker;
                };
                
                marker.addListener = function(eventName, handler) {
                    // Map Google Maps events to Leaflet events
                    var leafletEvent = eventName;
                    if (eventName === 'click') {
                        leafletEvent = 'click';
                    } else if (eventName === 'mouseover') {
                        leafletEvent = 'mouseover';
                    } else if (eventName === 'mouseout') {
                        leafletEvent = 'mouseout';
                    }
                    // Wrap handler to convert Leaflet event to Google Maps event format
                    // Use closure to capture marker reference for 'this' binding
                    var self = marker;
                    marker.on(leafletEvent, function(e) {
                        // Add Google Maps compatible latLng property
                        if (e.latlng) {
                            e.latLng = {
                                lat: function() { return e.latlng.lat; },
                                lng: function() { return e.latlng.lng; }
                            };
                            // Also add as direct properties for compatibility
                            e.latLng.lat = e.latlng.lat;
                            e.latLng.lng = e.latlng.lng;
                        }
                        // Call handler with marker as 'this' context
                        handler.call(self, e);
                    });
                    return marker;
                };
                
                // Copy all custom properties from options to marker
                // This preserves properties like airport, airportName, championIcon, etc.
                for (var key in options) {
                    if (options.hasOwnProperty(key) && 
                        !['position', 'map', 'title', 'icon', 'opacity'].includes(key)) {
                        marker[key] = options[key];
                    }
                }
                
                                console.log('✅ Marker created with custom properties:', 
                    'airport:', marker.airport ? marker.airport.name : 'none',
                    'isBase:', marker.isBase);
                
                // Store original position for world wrapping
                marker._originalLng = latlng.lng;
                marker._wrapOffset = 0;
                
                // Store original setLatLng for wrapping support
                var originalSetLatLng = marker.setLatLng.bind(marker);
                
                // Function to update marker position based on map view
                marker._updateWrappedPosition = function() {
                    if (!marker._map) return;
                    
                    var mapCenter = marker._map.getCenter();
                    var centerLng = mapCenter.lng;
                    var markerLng = marker._originalLng;
                    
                    // Find the best wrapped version of this longitude relative to map center
                    // Check -360, 0, +360 offsets and pick the closest one to center
                    var possibleLngs = [
                        markerLng - 360,
                        markerLng,
                        markerLng + 360
                    ];
                    
                    var bestLng = possibleLngs.reduce(function(best, lng) {
                        var distToBest = Math.abs(centerLng - best);
                        var distToThis = Math.abs(centerLng - lng);
                        return distToThis < distToBest ? lng : best;
                    });
                    
                    // Only update if position changed
                    if (bestLng !== latlng.lng) {
                        latlng.lng = bestLng;
                        originalSetLatLng(latlng);
                    }
                };
                
                // Store original addTo
                var originalAddTo = marker.addTo.bind(marker);
                
                // Override addTo to set up viewport tracking
                marker.addTo = function(map) {
                    originalAddTo(map);
                    marker._updateWrappedPosition();
                    return marker;
                };
                
                // Add to map if specified
                if (options.map) {
                    marker.addTo(options.map);
                }
            }
            
            return marker;
        },
        
        // Polyline constructor wrapper
        Polyline: function(options) {
            var latlngs = [];
            if (options.path) {
                latlngs = options.path.map(function(p) {
                    return [p.lat, p.lng];
                });
            }
            
            // Handle antimeridian crossing - adjust coordinates so Leaflet draws the shorter path
            latlngs = fixAntimeridianPath(latlngs);
            
            var polylineOptions = {
                color: options.strokeColor || '#FF0000',
                weight: options.strokeWeight || 2,
                opacity: options.strokeOpacity || 1.0
            };
            
            var polyline = L.polyline(latlngs, polylineOptions);
            
            // Add compatibility methods
            polyline.setMap = function(map) {
                if (map) {
                    polyline.addTo(map);
                } else {
                    polyline.remove();
                }
            };
            
            polyline.setPath = function(path) {
                var latlngs = path.map(function(p) {
                    return [p.lat, p.lng];
                });
                // Apply antimeridian fix to new path as well
                latlngs = fixAntimeridianPath(latlngs);
                polyline.setLatLngs(latlngs);
            };
            
            polyline.getPath = function() {
                var latlngs = polyline.getLatLngs().map(function(ll) {
                    return { lat: ll.lat, lng: ll.lng };
                });
                
                // Add getAt method for Google Maps compatibility
                latlngs.getAt = function(index) {
                    return latlngs[index];
                };
                
                return latlngs;
            };
            
            polyline.setOptions = function(options) {
                // Update polyline style options
                if (options.strokeColor !== undefined) {
                    polyline.setStyle({ color: options.strokeColor });
                }
                if (options.strokeWeight !== undefined) {
                    polyline.setStyle({ weight: options.strokeWeight });
                }
                if (options.strokeOpacity !== undefined) {
                    polyline.setStyle({ opacity: options.strokeOpacity });
                }
                // Store custom properties
                if (options.zIndex !== undefined) {
                    polyline.zIndex = options.zIndex;
                    if (polyline._path) {
                        polyline._path.style.zIndex = options.zIndex;
                    }
                }
                return polyline;
            };
            
            polyline.getMap = function() {
                return polyline._map || null;
            };
            
            polyline.addListener = function(eventName, handler) {
                // Map Google Maps events to Leaflet events
                var leafletEvent = eventName;
                if (eventName === 'click') {
                    leafletEvent = 'click';
                } else if (eventName === 'mouseover') {
                    leafletEvent = 'mouseover';
                } else if (eventName === 'mouseout') {
                    leafletEvent = 'mouseout';
                }
                // Wrap handler to convert Leaflet event to Google Maps event format
                // Use closure to capture polyline reference for 'this' binding
                var self = polyline;
                polyline.on(leafletEvent, function(e) {
                    // Add Google Maps compatible latLng property
                    if (e.latlng) {
                        e.latLng = {
                            lat: function() { return e.latlng.lat; },
                            lng: function() { return e.latlng.lng; }
                        };
                        // Also add as direct properties for compatibility
                        e.latLng.lat = e.latlng.lat;
                        e.latLng.lng = e.latlng.lng;
                    }
                    // Call handler with polyline as 'this' context
                    handler.call(self, e);
                });
                return polyline;
            };
            
            // Copy all custom properties from options to polyline
            // This preserves properties like link, originalZIndex, etc.
            for (var key in options) {
                if (options.hasOwnProperty(key) && 
                    !['path', 'map', 'strokeColor', 'strokeWeight', 'strokeOpacity'].includes(key)) {
                    polyline[key] = options[key];
                }
            }
            
            // Add Google Maps compatible property accessors
            Object.defineProperty(polyline, 'strokeColor', {
                get: function() {
                    return polyline.options.color;
                },
                set: function(value) {
                    polyline.setStyle({ color: value });
                }
            });
            
            Object.defineProperty(polyline, 'strokeWeight', {
                get: function() {
                    return polyline.options.weight;
                },
                set: function(value) {
                    polyline.setStyle({ weight: value });
                }
            });
            
            Object.defineProperty(polyline, 'strokeOpacity', {
                get: function() {
                    return polyline.options.opacity;
                },
                set: function(value) {
                    polyline.setStyle({ opacity: value });
                }
            });
            
            // Add to map if specified
            if (options.map) {
                polyline.addTo(options.map);
            }
            
            return polyline;
        },
        
        // InfoWindow constructor wrapper (popup)
        InfoWindow: function(options) {
            options = options || {};
            
            var popup = L.popup({
                maxWidth: options.maxWidth || 300,
                minWidth: options.minWidth || 50,
                maxHeight: options.maxHeight || null,
                autoPan: options.autoPan !== false,
                closeButton: true,
                autoClose: false,
                closeOnEscapeKey: true,
                className: 'leaflet-google-popup'
            });
            
            // Store content
            popup._content = options.content || '';
            popup.marker = null;
            
            // Add compatibility methods
            popup.setContent = function(content) {
                popup._content = content;
                L.Popup.prototype.setContent.call(popup, content);
                return popup;
            };
            
            popup.getContent = function() {
                return popup._content;
            };
            
            popup.setPosition = function(position) {
                if (position && position.lat !== undefined && position.lng !== undefined) {
                    // Store the position for later use when opening
                    popup._infoWindowPosition = [position.lat, position.lng];
                }
                return popup;
            };
            
            popup.open = function(map, marker) {
                popup.marker = marker;
                if (marker && marker.getLatLng) {
                    popup.setLatLng(marker.getLatLng());
                } else if (popup._infoWindowPosition) {
                    // Use previously set position if no marker
                    popup.setLatLng(popup._infoWindowPosition);
                }
                if (popup._map !== map) {
                    popup.openOn(map);
                }
                return popup;
            };
            
            popup.close = function() {
                if (popup._map) {
                    // Call Leaflet's native remove method to avoid recursion
                    popup.remove();
                }
                return popup;
            };
            
            // Set initial content if provided
            if (options.content) {
                popup.setContent(options.content);
            }
            
            return popup;
        },
        
        // Event system wrapper
        event: {
            addListener: function(instance, eventName, handler) {
                // Map Google Maps events to Leaflet events
                var leafletEvent = eventName;
                switch(eventName) {
                    case 'zoom_changed':
                        leafletEvent = 'zoomend';
                        break;
                    case 'maptypeid_changed':
                        leafletEvent = 'baselayerchange';
                        break;
                    case 'bounds_changed':
                        leafletEvent = 'moveend';
                        break;
                    case 'click':
                        leafletEvent = 'click';
                        break;
                }
                
                if (instance.on) {
                    // Wrap handler to convert Leaflet event to Google Maps event format
                    instance.on(leafletEvent, function(e) {
                        // Add Google Maps compatible latLng property
                        if (e && e.latlng) {
                            e.latLng = {
                                lat: function() { return e.latlng.lat; },
                                lng: function() { return e.latlng.lng; }
                            };
                            // Also add as direct properties for compatibility
                            e.latLng.lat = e.latlng.lat;
                            e.latLng.lng = e.latlng.lng;
                        }
                        handler(e);
                    });
                }
            },
            
            removeListener: function(listener) {
                if (listener && listener.off) {
                    listener.off();
                }
            },
            
            clearListeners: function(instance, eventName) {
                if (instance && instance.off) {
                    if (eventName) {
                        // Map Google Maps event names to Leaflet events
                        var leafletEvent = eventName;
                        if (eventName === 'mouseover') leafletEvent = 'mouseover';
                        else if (eventName === 'mouseout') leafletEvent = 'mouseout';
                        else if (eventName === 'click') leafletEvent = 'click';
                        
                        instance.off(leafletEvent);
                    } else {
                        // Clear all listeners
                        instance.off();
                    }
                }
            },
            
            clearInstanceListeners: function(instance, eventName) {
                // Alias for clearListeners - Google Maps compatibility
                this.clearListeners(instance, eventName);
            },
            
            trigger: function(instance, eventName) {
                // Handle special events
                if (eventName === 'resize') {
                    // For Leaflet maps, invalidateSize is the equivalent of resize
                    if (instance && instance.invalidateSize) {
                        instance.invalidateSize();
                    }
                } else if (instance && instance.fire) {
                    // Fire the event on Leaflet objects
                    instance.fire(eventName);
                }
            }
        },
        
        // Geometry spherical functions
        geometry: {
            spherical: LeafletGeometry
        },
        
        // ControlPosition enum (Google Maps uses numeric values)
        ControlPosition: {
            TOP_LEFT: 0,
            TOP_CENTER: 1,
            TOP_RIGHT: 2,
            LEFT_TOP: 3,
            LEFT_CENTER: 4,
            LEFT_BOTTOM: 5,
            RIGHT_TOP: 6,
            RIGHT_CENTER: 7,
            RIGHT_BOTTOM: 8,
            BOTTOM_LEFT: 9,
            BOTTOM_CENTER: 10,
            BOTTOM_RIGHT: 11
        },
        
        // LatLng constructor
        LatLng: function(latOrOptions, lng) {
            // Support both LatLng(lat, lng) and LatLng({lat, lng}) formats
            if (typeof latOrOptions === 'object' && latOrOptions !== null) {
                this.lat = latOrOptions.lat;
                this.lng = latOrOptions.lng;
            } else {
                this.lat = latOrOptions;
                this.lng = lng;
            }
        },
        
        // Point constructor for icon anchors
        Point: function(x, y) {
            this.x = x;
            this.y = y;
        },
        
        // Size constructor for icon sizes
        Size: function(width, height) {
            this.width = width;
            this.height = height;
        }
    };
}

// Export utilities
window.LeafletGeometry = LeafletGeometry;
window.ControlPositions = ControlPositions;
window.convertLatLng = convertLatLng;
window.addArrowDecorator = addArrowDecorator;

console.log('Leaflet Adapter loaded - OpenStreetMap migration ready! 🗺️');
