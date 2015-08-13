const TEMPLATES_FOLDER = "ui_components/artifactory_grid/templates/";
let headerCellTemplate = require("raw!./templates/headerCellDefaultTemplate.html");
let $timeout;
const COMMON_ACTIONS = {
    delete: {
        icon: 'icon icon-clear',
        tooltip: 'Delete'
    },
    download: {
        icon: 'icon icon-download',
        tooltip: 'Download'
    }
};

class ArtifactoryGrid {

    constructor(scope, uiGridConstants) {
        this.scope = scope;

        if (scope) {
            this.appScopeProvider = scope;
        }
        this.enableRowSelection = true;
        this.enableRowHeaderSelection = false;
        this.modifierKeysToMultiSelect = false;
        this.multiSelect = false;
        this.noUnselect = false;
        this.enableColumnMenus = false;
        this.rowHeight = 40;
        this.headerRowHeight = 41;
        this.enableHorizontalScrollbar = uiGridConstants.scrollbars.NEVER;
        this.enableVerticalScrollbar = uiGridConstants.scrollbars.NEVER;
        this.groupingShowCounts = false;
        this._afterRegister = [];

        // pagination
        this.paginationCallback = null;
        this.enablePagination = true;
        this.enablePaginationControls = false;
        this.paginationPageSize = 25;
        this.resetPagination();
    }

    resetPagination() {
        this.paginationCurrentPage = 1;
    }

    getPagination() {
        let pagination = {
            pageNum: this.paginationCurrentPage,
            numOfRows: this.paginationPageSize
        }
        let sortColumn = this.getSortColumn();
        if (sortColumn) {
            pagination.direction = sortColumn.sort.direction;
            pagination.orderBy = sortColumn.field || sortColumn.name;
        }
        else {
            pagination.direction = 'asc';
            pagination.orderBy = this.columnDefs[0].field;
        }
        return pagination;
    }

    getSortColumn() {
        if (_.isEmpty(this.api.grid.columns)) {
            return _.findWhere(this.columnDefs, {sort: {}});
        }
        else {
            return this.api.grid.getColumnSorting()[0];
        }
    }

    setExternalPagination(callback) {
        // set external pagination params
        this.useExternalPagination = true;
        this.useExternalSorting = true;
        this.paginationCallback = callback;
        // register on sort and on page change callbacks
        this.afterRegister((gridApi) => {
            gridApi.core.on.sortChanged(this.scope, (grid, sortColumns) => {
                this.getPage();
            });
            gridApi.pagination.on.paginationChanged(this.scope, (pageNumber, pageSize) => {
                this.getPage();
            });
            // get initial page
            this.getPage();
        });
        return this;
    }

    getPage() {
        if (!this.paginationCallback) {
            return;
        }
        this.paginationCallback(this.getPagination())
                .then((pagedResponse) => {
                    this.totalItems = pagedResponse.totalItems;
                    this.setGridData(pagedResponse.pagingData);
                });
    }

    afterRegister(callback) {
        // If api is already registered - invoke the callback
        if (this.api) {
            callback(this.api);
        }
        // Add it to array anyway (for cases when grid element is removed and added to DOM with ng-if)
        this._afterRegister.push(callback);
    }

    setColumns(columnDefs) {
        this.columnDefs = columnDefs;
        this.columnDefs.forEach((item) => {
            if (!item.headerCellTemplate) {
                item.headerCellTemplate = headerCellTemplate;
            }
            // enableCellEdit is by default true. If not defined - we want it to be false
            if (!item.enableCellEdit) {
                item.enableCellEdit = false;
            }
            // If given default actions, fetch their data from the default actions dictionary and add to the actions array
            if (item.actions) {
                item.customActions = item.customActions || [];
                _.forEach(item.actions, (callback, key) => {
                    let action;
                    if (callback.visibleWhen) {
                        action = this._getCommonAction(key, callback.callback, callback.visibleWhen);
                    } else {
                        action = this._getCommonAction(key, callback);
                    }
                    item.customActions.push(action);
                });
            }
        });
        return this;
    }

    // Get default action by key and append the callback
    _getCommonAction(key, callback, visibleWhen) {
        let action = COMMON_ACTIONS[key];
        action = angular.extend({callback: callback}, action);
        if (visibleWhen) {
            action = angular.extend({visibleWhen: visibleWhen}, action);
        }
        return action;
    }

    setButtons(buttons) {
        if (!this.scope) {
            throw 'Must set scope to use buttons';
        }
        this.buttons = buttons;
        return this;
    }

    setBatchActions(batchActions) {
        this.batchActions = batchActions;
        this.setMultiSelect();
        return this;
    }

    setGridData(data) {
        this.data = data;
        this.afterRegister((gridApi) => {
            gridApi.grid.element.css('visibility', 'visible');
        });

        if (!this.data || !this.data.length) {
            // if the grid is empty push an empty object
            this.data = [{_emptyRow: true}];
            // Also disable select all in header
            this.enableSelectAll = false;
        }
        else {
            // In case select all was chosen, re-enable it after data was added
            this.enableSelectAll = this._allowMultiSelect;
        }

        this._resize();

        return this;
    }


    _resize() {
        let dataLen = this.api ? this.api.core.getVisibleRows().length : this.data.length;
        // at least 1 row
        this.minRowsToShow = Math.max(1, dataLen);

        // if grid is already displayed - recalculate its height. ui-grid doesn't have an API call for this
        if (this.api) {
            let grid = this.api.grid;
            let height = this.minRowsToShow * grid.options.rowHeight + grid.options.headerRowHeight;

            // (Adam) This is instead of ui-grid-auto-resize which has a 250ms interval that causes the grid to flicker
            grid.element.css('height', height + 'px');
            grid.element.find('.ui-grid-viewport').css('height', height - this.headerRowHeight + 'px');
            grid.gridHeight = height;
        }
    }

    onRegisterApi(gridApi) {
        this.api = gridApi;

        if (this.scope === undefined) {
            this.scope = null;
        }
        this.api.core.on.rowsRendered(this.scope, () => {
            this._resize();
        });

        if (this.onSelectionChange && this.api.selection) {
            this.api.selection.on.rowSelectionChanged(this.scope, this.onSelectionChange);
        }
        if (this.onSelectionChangeBatch && this.api.selection) {
            this.api.selection.on.rowSelectionChangedBatch(this.scope, this.onSelectionChangeBatch);
        }


        if (this.scope) {
            if (!this.scope.grids) {
                this.scope.grids = {};
            }
            this.scope.grids[gridApi.grid.id] = {buttons: this.buttons};
        }

        this._afterRegister.forEach((callback) => {
            callback(gridApi);
        });
        return this;
    }

    setRowTemplate(fileName) {
        if (fileName) {
            this.rowTemplate = TEMPLATES_FOLDER + fileName + '.html';
        }
        return this;
    }

    setDraggable(callbackFunc) {
        this.setRowTemplate('drag_rows');
        this.draggablefunc = callbackFunc;
        this.afterRegister((gridApi) => {
            gridApi.draggableRows.on.rowDropped(this.scope, (info, dropTarget) => {
                this.draggablefunc(info, dropTarget);
            });
        });
        return this;
    }

    setMultiSelect() {
        this.enableRowHeaderSelection = true;

        this.multiSelect = true;
        this.enableSelectAll = true;
        this._allowMultiSelect = true;
        this.selectionRowHeaderWidth = 40;
        return this;
    }

    setSingleSelect() {
        this.enableRowHeaderSelection = true;
        this.multiSelect = false;
        this.enableSelectAll = true;
        this._allowMultiSelect = false;
        this.selectionRowHeaderWidth = 40;

        return this;
    }

    setMinRowToShow(number) {
        this.minRowsToShow = number;
        return this;
    }

    selectItem(item) {
        $timeout(() => this.api.selection.selectRow(item));
    }

}


export class ArtifactoryGridFactory {
    constructor(uiGridConstants, _$timeout_) {
        $timeout = _$timeout_;
        this.uiGridConstants = uiGridConstants;
    }

    getGridInstance(scope) {
        return new ArtifactoryGrid(scope, this.uiGridConstants);
    }

}