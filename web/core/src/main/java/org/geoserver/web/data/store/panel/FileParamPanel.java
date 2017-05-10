/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.store.panel;

import java.io.File;
import java.io.FileFilter;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.validation.FormComponentFeedbackBorder;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.validation.IValidator;
import org.geoserver.web.wicket.browser.GeoServerFileChooser;

/**
 * A label, a text field, a file chooser
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class FileParamPanel extends Panel implements ParamPanel {

	private static final long serialVersionUID = 2630421795437249103L;
	TextField<String> textField;
    ModalWindow dialog;
    IModel<? extends FileFilter> fileFilter;
    
    /**
     * 
     * @param id
     * @param paramsMap
     * @param paramName
     * @param paramLabelModel
     * @param required
     * @param validators
     *            any extra validator that should be added to the input field, or {@code null}
     */
    @SafeVarargs
	public FileParamPanel(final String id, final IModel<String> paramValue, final IModel<String> paramLabelModel,
            final boolean required, IValidator<? super String>... validators) {
        // make the value of the text field the model of this panel, for easy value retrieval
        super(id, paramValue);
        
        // add the dialog for the file chooser
        add(dialog = new ModalWindow("dialog"));

        // the label
        String requiredMark = required ? " *" : ""; 
        Label label = new Label("paramName", paramLabelModel.getObject() + requiredMark);
        add(label);

        // the text field, with a decorator for validations
        textField = new TextField<String>("paramValue", new FileModel(paramValue));
        textField.setRequired(required);
        textField.setOutputMarkupId(true);
        // set the label to be the paramLabelModel otherwise a validation error would look like
        // "Parameter 'paramValue' is required"
        textField.setLabel(paramLabelModel);

        if (validators != null) {
            for (IValidator<? super String> validator : validators) {
                textField.add(validator);
            }
        }
        
        FormComponentFeedbackBorder feedback = new FormComponentFeedbackBorder("border");
        feedback.add(textField);
        feedback.add(chooserButton(paramLabelModel.getObject()));
        add(feedback);
    }
    
    protected Component chooserButton(final String windowTitle) {
        AjaxSubmitLink link = new AjaxSubmitLink("chooser") {
            
			private static final long serialVersionUID = -6640131658256808053L;

			@Override
            public boolean getDefaultFormProcessing() {
                return false;
            }
            
            @Override
            public void onSubmit(AjaxRequestTarget target, Form<?> form) {
                File file = null;
                textField.processInput();
                String input = textField.getConvertedInput();
                if (input != null && !input.equals("")) {
                    file = new File(input);
                }

                GeoServerFileChooser chooser = new GeoServerFileChooser(dialog.getContentId(), new Model<File>(file)) {
                    private static final long serialVersionUID = -7096642192491726498L;

                    protected void fileClicked(File file, AjaxRequestTarget target) {
                      // clear the raw input of the field won't show the new model value
                      textField.clearInput();
                      textField.setModelObject(file.getAbsolutePath());

                      target.add(textField);
                      dialog.close(target);
                    };
                };
                chooser.setFileTableHeight(null);
                chooser.setFilter(fileFilter);
                dialog.setContent(chooser);
                dialog.setTitle(windowTitle);
                dialog.show(target);
            }

        };
        return link;
    }

    /**
     * The text field stored inside the panel. 
     *
     */
    public FormComponent<String> getFormComponent() {
        return textField;
    }
    
    /**
     * Sets the filter that will act in the file chooser dialog
     * @param fileFilter
     */
    public void setFileFilter(IModel<? extends FileFilter> fileFilter) {
        this.fileFilter = fileFilter;
    }
    
    
}
