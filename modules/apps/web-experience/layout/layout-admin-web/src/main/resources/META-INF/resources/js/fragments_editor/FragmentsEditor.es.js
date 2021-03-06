import Component from 'metal-component';
import debounce from 'metal-debounce';
import {Config} from 'metal-state';
import {getUid} from 'metal';
import Soy from 'metal-soy';

import '../contextual_sidebar/ContextualSidebar.es';
import './FragmentEntryLink.es';
import './sidebar/SidebarAddedFragment.es';
import './sidebar/SidebarFragmentCollection.es';
import templates from './FragmentsEditor.soy';

/**
 * FragmentsEditor
 * @review
 */
class FragmentsEditor extends Component {
	/**
	 * @inheritDoc
	 * @review
	 */
	created() {
		this._updatePageTemplate = this._updatePageTemplate.bind(this);
		this._updatePageTemplate = debounce(this._updatePageTemplate, 1000);

		this._initializeEditables();
	}

	/**
	 * If there are changes on any fragment, it sets the _dirty property
	 * to true and queues an update.
	 * @inheritDoc
	 * @param {!Object} changes
	 * @review
	 */
	shouldUpdate(changes) {
		if (changes.fragmentEntryLinks || changes._editables) {
			this._dirty = true;
			this._updatePageTemplate();
		}

		return true;
	}

	/**
	 * Callback executed everytime an editable field has been changed
	 * @param {{
	 *   editableId: string,
	 *   fragmentIndex: number,
	 *   value: string
	 * }} data
	 * @private
	 * @review
	 */
	_handleEditableChanged(data) {
		const index = this._editables.findIndex(
			editable =>
				editable.editableId === data.editableId &&
				editable.fragmentIndex === data.fragmentIndex
		);

		const editable = {
			editableId: data.editableId,
			fragmentIndex: data.fragmentIndex,
			value: data.value,
		};

		if (index === -1) {
			this._editables = [...this._editables, editable];
		} else {
			this._editables = [
				...this._editables.slice(0, index),
				...this._editables.slice(index + 1),
				editable,
			];
		}
	}

	/**
	 * Callback executed when a fragment entry of a collection is clicked.
	 * It receives fragmentEntryId and fragmentName as event data.
	 * @param {!Event} event
	 * @private
	 * @review
	 */
	_handleFragmentCollectionEntryClick(event) {
		this.fragmentEntryLinks = [
			...this.fragmentEntryLinks,
			{
				fragmentEntryId: event.fragmentEntryId,
				fragmentEntryLinkId: getUid().toString(),
				name: event.fragmentName,
				config: {},
			},
		];
	}

	/**
	 * Removes a fragment from the fragment list. The fragment to
	 * be removed should be specified inside the event as fragmentIndex
	 * @param {!Event} event
	 * @private
	 * @review
	 */
	_handleFragmentRemoveButtonClick(event) {
		const index = event.fragmentIndex;

		this._editables = this._editables
			.filter(editable => editable.fragmentIndex !== index)
			.map(editable => ({
				editableId: editable.editableId,
				fragmentIndex:
					editable.fragmentIndex > index
						? editable.fragmentIndex - 1
						: editable.fragmentIndex,
				value: editable.value,
			}));

		this.fragmentEntryLinks = [
			...this.fragmentEntryLinks.slice(0, index),
			...this.fragmentEntryLinks.slice(index + 1),
		];
	}

	/**
	 * Callback executed when the sidebar should be hidden
	 * @private
	 * @review
	 */
	_handleHideContextualSidebar() {
		this._contextualSidebarVisible = false;
	}

	/**
	 * Updates _sidebarSelectedTab according to the clicked element
	 * @param {!Event} event
	 * @private
	 * @review
	 */
	_handleSidebarTabClick(event) {
		this._sidebarSelectedTab = event.delegateTarget.dataset.tabName;
	}

	/**
	 * Callback executed when the sidebar visible state should be toggled
	 * @private
	 * @review
	 */
	_handleToggleContextualSidebarButtonClick() {
		this._contextualSidebarVisible = !this._contextualSidebarVisible;
	}

	/**
	 * Initialize _editables property with the existing values received inside
	 * fragmentEntryLinks.
	 * @private
	 */
	_initializeEditables() {
		const editables = [];

		this.fragmentEntryLinks.forEach((fragment, index) => {
			for (let key of Object.keys(fragment.editableValues || {})) {
				editables.push({
					editableId: key,
					fragmentIndex: index,
					value: fragment.editableValues[key],
				});
			}
		});

		this._editables = editables;
	}

	/**
	 * Sends all the accumulated changes to the server and, if
	 * success, sets the _dirty property to false.
	 * @private
	 * @review
	 */
	_updatePageTemplate() {
		this._dirty = false;

		const formData = new FormData();

		formData.append(
			`${this.portletNamespace}classPK`,
			this.classPK
		);

		const editableValues = {};

		this._editables.forEach(editable => {
			editableValues[editable.fragmentIndex] =
				editableValues[editable.fragmentIndex] || {};

			editableValues[editable.fragmentIndex][editable.editableId] =
				editable.value;
		});

		formData.append(
			`${this.portletNamespace}editableValues`,
			JSON.stringify(editableValues)
		);

		this.fragmentEntryLinks.forEach(fragment => {
			formData.append(
				`${this.portletNamespace}fragmentIds`,
				fragment.fragmentEntryId
			);
		});

		fetch(this.updateURL, {
			body: formData,
			credentials: 'include',
			method: 'POST',
		}).then(() => {
			this._lastSaveDate = new Date().toLocaleTimeString();
			this._dirty = false;
		});
	}
}

/**
 * Tabs that can appear inside the sidebar
 * @review
 * @see FragmentsEditor._sidebarTabs
 */
const SIDEBAR_TABS = [
	{
		id: 'fragments',
		name: Liferay.Language.get('fragments'),
		visible: true,
	},
	{
		id: 'added',
		name: Liferay.Language.get('added'),
		visible: true,
	},
];

/**
 * State definition.
 * @review
 * @static
 * @type {!Object}
 */
FragmentsEditor.STATE = {
	/**
	 * Class primary key used for storing changes.
	 * @default undefined
	 * @instance
	 * @memberOf FragmentsEditor
	 * @review
	 * @type {!string}
	 */
	classPK: Config.string().required(),

	/**
	 * Available entries that can be used, organized by collections.
	 * @default undefined
	 * @instance
	 * @memberOf FragmentsEditor
	 * @review
	 * @type {!Array<object>}
	 */
	fragmentCollections: Config.arrayOf(
		Config.shapeOf({
			fragmentCollectionId: Config.string().required(),
			name: Config.string().required(),
			entries: Config.arrayOf(
				Config.shapeOf({
					fragmentEntryId: Config.string().required(),
					name: Config.string().required(),
				})
			).required(),
		})
	).required(),

	/**
	 * Optional ID provided by the template system.
	 * @default ''
	 * @instance
	 * @memberOf FragmentsEditor
	 * @review
	 * @type {string}
	 */
	id: Config.string().value(''),

	/**
	 * List of fragment instances being used, the order
	 * of the elements in this array defines their position.
	 * @default []
	 * @instance
	 * @memberOf FragmentsEditor
	 * @review
	 * @type {Array<string>}
	 */
	fragmentEntryLinks: Config.arrayOf(
		Config.shapeOf({
			fragmentEntryId: Config.string().required(),
			fragmentEntryLinkId: Config.string().required(),
			name: Config.string().required(),
			editableValues: Config.object().value({}),
			config: Config.object().value({}),
		})
	).value([]),

	/**
	 * Portlet namespace needed for prefixing form inputs
	 * @default undefined
	 * @instance
	 * @memberOf FragmentsEditor
	 * @review
	 * @type {!string}
	 */
	portletNamespace: Config.string().required(),

	/**
	 * URL for getting a fragment content.
	 * @default undefined
	 * @instance
	 * @memberOf FragmentsEditor
	 * @review
	 * @type {!string}
	 */
	renderFragmentEntryURL: Config.string().required(),

	/**
	 * Path of the available icons.
	 * @default undefined
	 * @instance
	 * @memberOf FragmentsEditor
	 * @review
	 * @type {!string}
	 */
	spritemap: Config.string().required(),

	/**
	 * URL for updating accumulated changes.
	 * @default undefined
	 * @instance
	 * @memberOf FragmentsEditor
	 * @review
	 * @type {!string}
	 */
	updateURL: Config.string().required(),

	/**
	 * Allow opening/closing contextual sidebar
	 * @default true
	 * @instance
	 * @memberOf FragmentsEditor
	 * @private
	 * @review
	 * @type {boolean}
	 */
	_contextualSidebarVisible: Config.bool()
		.internal()
		.value(true),

	/**
	 * When true, it indicates that are changes pending to save.
	 * @default false
	 * @instance
	 * @memberOf FragmentsEditor
	 * @private
	 * @review
	 * @type {boolean}
	 */
	_dirty: Config.bool()
		.internal()
		.value(false),

	/**
	 * List of editable fields that have been modified by the user
	 * @default []
	 * @instance
	 * @memberOf FragmentsEditor
	 * @private
	 * @type {{
	 *   editableId: string,
	 *   fragmentIndex: number,
	 *   value: string
	 * }}
	 */
	_editables: Config
		.arrayOf(Config.shapeOf({
			editableId: Config.string(),
			fragmentIndex: Config.number(),
			value: Config.string(),
		}))
		.internal()
		.value([]),

	/**
	 * Last data when the autosave has been executed.
	 * @default ''
	 * @instance
	 * @memberOf FragmentsEditor
	 * @private
	 * @type {string}
	 */
	_lastSaveDate: Config.string()
		.internal()
		.value(''),

	/**
	 * Tabs being shown in sidebar
	 * @default SIDEBAR_TABS
	 * @instance
	 * @memberOf FragmentsEditor
	 * @private
	 * @review
	 * @type {Array<{
	 * 	 id:string,
	 * 	 name:string,
	 * 	 visible:boolean
	 * }>}
	 */
	_sidebarTabs: Config.arrayOf(
		Config.shapeOf({
			id: Config.string(),
			name: Config.string(),
			visible: Config.bool(),
		})
	)
		.internal()
		.value(SIDEBAR_TABS),

	/**
	 * Tab selected inside sidebar
	 * @default SIDEBAR_TABS[0].id
	 * @instance
	 * @memberOf FragmentsEditor
	 * @private
	 * @review
	 * @type {string}
	 */
	_sidebarSelectedTab: Config.oneOf(SIDEBAR_TABS.map(tab => tab.id))
		.internal()
		.value(SIDEBAR_TABS[0].id),
};

Soy.register(FragmentsEditor, templates);

export {FragmentsEditor};
export default FragmentsEditor;
