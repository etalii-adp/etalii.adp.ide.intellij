package etalii.adp.core.diagram.view;

import javax.swing.JTable;

import etalii.adp.core.diagram.EditorKind;

/**
 * What the ADP Properties tool window's content offers besides its table: which property each row
 * shows. The table has the label in column 0 and the value, with its cell editor, in column 1. The
 * test kit finds it in the tool window by this interface.
 */
public interface PropertiesContent {

    /** The diagram whose selection the rows show, or {@code null} while the content follows none. */
    DiagramFileEditor tool();

    /** The rows for the current selection. */
    JTable table();

    /** The id of the property a row shows, or {@code null} for a row that shows none, such as a category. */
    String propertyId(int row);

    /** The editor kind of a property row. */
    EditorKind editor(int row);

    /** True when the selected items have different values for the row's property. */
    boolean mixed(int row);
}
