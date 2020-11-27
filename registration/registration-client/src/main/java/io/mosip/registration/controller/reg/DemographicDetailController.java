package io.mosip.registration.controller.reg;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.mvel2.MVEL;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;

import io.mosip.commons.packet.dto.packet.SimpleDto;
import io.mosip.kernel.core.applicanttype.exception.InvalidApplicantArgumentException;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.idvalidator.exception.InvalidIDException;
import io.mosip.kernel.core.idvalidator.spi.PridValidator;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.transliteration.spi.Transliteration;
import io.mosip.kernel.core.util.StringUtils;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.FXUtils;
import io.mosip.registration.controller.VirtualKeyboard;
import io.mosip.registration.controller.device.BiometricsController;
import io.mosip.registration.dao.MasterSyncDao;
import io.mosip.registration.dto.ErrorResponseDTO;
import io.mosip.registration.dto.RegistrationDTO;
import io.mosip.registration.dto.RequiredOnExpr;
import io.mosip.registration.dto.ResponseDTO;
import io.mosip.registration.dto.SuccessResponseDTO;
import io.mosip.registration.dto.Field;
import io.mosip.registration.dto.Validator;
import io.mosip.registration.dto.mastersync.DocumentCategoryDto;
import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.registration.dto.mastersync.LocationDto;
import io.mosip.registration.dto.schema.Group;
import io.mosip.registration.dto.schema.Screen;
import io.mosip.registration.entity.Location;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.service.IdentitySchemaService;
import io.mosip.registration.service.sync.MasterSyncService;
import io.mosip.registration.service.sync.PreRegistrationDataSyncService;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * {@code DemographicDetailController} is to capture the demographic details
 * 
 * @author Taleev.Aalam
 * @since 1.0.0
 *
 */

@Controller
public class DemographicDetailController extends BaseController {

	/**
	 * Instance of {@link Logger}
	 */
	private static final Logger LOGGER = AppConfig.getLogger(DemographicDetailController.class);

	@FXML
	public TextField preRegistrationId;

	@FXML
	public Label languageLabelLocalLanguage;

	@FXML
	private GridPane parentDetailPane;
	@FXML
	private ScrollPane parentScrollPane;
	private boolean isChild;
	private Node keyboardNode;
	@Autowired
	private PridValidator<String> pridValidatorImpl;

	@Autowired
	private Validations validation;
	@Autowired
	private MasterSyncService masterSync;
	@FXML
	private FlowPane parentFlowPane;
	@FXML
	private GridPane scrollParentPane;
	@FXML
	private GridPane preRegParentPane;
	@Autowired
	private DateValidation dateValidation;
	@Autowired
	private RegistrationController registrationController;
	@Autowired
	private DocumentScanController documentScanController;
	@Autowired
	private Transliteration<String> transliteration;
	@Autowired
	private PreRegistrationDataSyncService preRegistrationDataSyncService;

	private FXUtils fxUtils;
	private int minAge;
	private int maxAge;

	@FXML
	private HBox parentDetailsHbox;
	@FXML
	private HBox localParentDetailsHbox;
	@FXML
	private AnchorPane ridOrUinToggle;

	@Autowired
	private MasterSyncService masterSyncService;
	@FXML
	private GridPane borderToDo;
	@FXML
	private Label registrationNavlabel;
	@FXML
	private AnchorPane keyboardPane;
	private boolean lostUIN = false;
	private ResourceBundle applicationLabelBundle;
	private ResourceBundle localLabelBundle;
	private String primaryLanguage;
	private String secondaryLanguage;
//	private List<String> orderOfAddress;
	boolean hasToBeTransliterated = true;
	// public Map<String, ComboBox<String>> listOfComboBoxWithString;
	public Map<String, ComboBox<GenericDto>> listOfComboBoxWithObject;
	public Map<String, List<Button>> listOfButtons;
	public Map<String, GridPane> listOfScreenGridPanes = new LinkedHashMap<>();
	public Map<String, TextField> listOfTextField;
	private int age = 0;
	@Autowired
	private IdentitySchemaService identitySchemaService;
	@Autowired
	private MasterSyncDao masterSyncDao;
	private VirtualKeyboard vk;
	private HashMap<String, Integer> positionTracker;
	int lastPosition;
	private ObservableList<Node> parentFlow;
	private boolean keyboardVisible = false;

	@Autowired
	private BiometricsController guardianBiometricsController;

	private Map<String, TreeMap<Integer, String>> orderOfAddressMapByGroup = new HashMap<>();

	private Map<String, List<String>> orderOfAddressListByGroup = new LinkedHashMap<>();
	// TODO find template based group fields
	Map<String, List<Field>> templateGroup = null;

	private Map<String, GridPane> demoGraphicScreenGridPaneMap = new LinkedHashMap<String, GridPane>();

	/*
	 * (non-Javadoc)
	 * 
	 * @see javafx.fxml.Initializable#initialize()
	 */
	@FXML
	private void initialize() throws IOException {

		LOGGER.debug(RegistrationConstants.REGISTRATION_CONTROLLER, APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Entering the Demographic Details Screen");

		// listOfComboBoxWithString = new HashMap<>();
		listOfComboBoxWithObject = new HashMap<>();
		listOfButtons = new HashMap<>();
		listOfTextField = new HashMap<>();
		lastPosition = -1;
		positionTracker = new HashMap<>();
		fillOrderOfLocation();
		primaryLanguage = applicationContext.getApplicationLanguage();
		secondaryLanguage = applicationContext.getLocalLanguage();

		ResourceBundle localProperties = ApplicationContext.localLanguageProperty();

		String localLanguageTextVal = isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()
				? localProperties.getString("language")
				: RegistrationConstants.EMPTY;
		languageLabelLocalLanguage.setText(localLanguageTextVal);

		if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
			vk = VirtualKeyboard.getInstance();
			keyboardNode = vk.view();
		}
		if (ApplicationContext.getInstance().getApplicationLanguage()
				.equals(ApplicationContext.getInstance().getLocalLanguage())) {
			hasToBeTransliterated = false;
		}

		try {
			applicationLabelBundle = applicationContext.getApplicationLanguageBundle();
			if (getRegistrationDTOFromSession() == null) {
				validation.updateAsLostUIN(false);
				registrationController.createRegistrationDTOObject(RegistrationConstants.PACKET_TYPE_NEW);
			}

			if (validation.isLostUIN()) {
				registrationNavlabel.setText(applicationLabelBundle.getString("/lostuin"));
				disablePreRegFetch();
			}

			if (getRegistrationDTOFromSession() != null
					&& getRegistrationDTOFromSession().getSelectionListDTO() == null) {
				getRegistrationDTOFromSession().setUpdateUINNonBiometric(false);
				getRegistrationDTOFromSession().setUpdateUINChild(false);
			}
			validation.setChild(false);
			lostUIN = false;
			fxUtils = FXUtils.getInstance();
			fxUtils.setTransliteration(transliteration);
			isChild = false;
			minAge = Integer.parseInt(getValueFromApplicationContext(RegistrationConstants.MIN_AGE));
			maxAge = Integer.parseInt(getValueFromApplicationContext(RegistrationConstants.MAX_AGE));
			localLabelBundle = applicationContext.getLocalLanguageProperty();
			parentFlow = parentFlowPane.getChildren();
			int position = parentFlow.size() - 1;

//			for (Entry<String, UiSchemaDTO> entry : validation.getValidationMap().entrySet()) {

			initializeDemoScreens(position);

			populateDropDowns();
			for (Entry<String, List<String>> orderOfAdd : orderOfAddressListByGroup.entrySet()) {
				List<String> orderOfAddress = orderOfAdd.getValue();
				addFirstOrderAddress(listOfComboBoxWithObject.get(orderOfAddress.get(0)), 1,
						applicationContext.getApplicationLanguage());

				if (isLocalLanguageAvailable() || !isAppLangAndLocalLangSame()) {

					addFirstOrderAddress(
							listOfComboBoxWithObject.get(orderOfAddress.get(0) + RegistrationConstants.LOCAL_LANGUAGE),
							1, applicationContext.getLocalLanguage());
				}

				for (int j = 0; j < orderOfAddress.size() - 1; j++) {
					final int k = j;

					try {
						listOfComboBoxWithObject.get(orderOfAddress.get(k)).setOnAction((event) -> {
							configureMethodsForAddress(k, k + 1, orderOfAddress.size(), orderOfAddress);
						});
					} catch (Exception runtimeException) {
						LOGGER.info(orderOfAddress.get(k) + " is not a valid field", APPLICATION_NAME,
								RegistrationConstants.APPLICATION_ID,
								runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
					}
				}
			}
			auditFactory.audit(AuditEvent.REG_DEMO_CAPTURE, Components.REGISTRATION_CONTROLLER,
					SessionContext.userContext().getUserId(), AuditReferenceIdTypes.USER_ID.getReferenceTypeId());
		} catch (RuntimeException runtimeException) {
			LOGGER.error("REGISTRATION - CONTROLLER", APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.UNABLE_LOAD_DEMOGRAPHIC_PAGE);

		}
	}

	private void addGroupInUI(List subList, int position, GridPane groupGridPane) {

		addGroupContent(subList, groupGridPane);

		position++;
		positionTracker.put(groupGridPane.getId(), position);
	}

	private void fillOrderOfLocation() {
		List<Location> locations = masterSyncDao.getLocationDetails(applicationContext.getApplicationLanguage());
		Map<Integer, String> treeMap = new TreeMap<Integer, String>();

		Collection<Field> fields = getUiSchemaFieldMap().values();
		for (Location location : locations) {
			List<Field> matchedfield = fields.stream()
					.filter(field -> isDemographicField(field) && field.getSubType() != null
							&& RegistrationConstants.DROPDOWN.equals(field.getControlType())
							&& field.getSubType().equalsIgnoreCase(location.getHierarchyName()))
					.collect(Collectors.toList());

			if (matchedfield != null && !matchedfield.isEmpty()) {
				for (Field field : matchedfield) {

					if (orderOfAddressMapByGroup.containsKey(field.getGroup())) {

						if (!orderOfAddressMapByGroup.get(field.getGroup()).containsKey(location.getHierarchyLevel())) {
							TreeMap<Integer, String> hirearchyMap = orderOfAddressMapByGroup.get(field.getGroup());
							hirearchyMap.put(location.getHierarchyLevel(), field.getId());

							orderOfAddressMapByGroup.put(field.getGroup(), hirearchyMap);
						}
					} else {

						TreeMap<Integer, String> hirearchyMap = new TreeMap<>();
						hirearchyMap.put(location.getHierarchyLevel(), field.getId());

						orderOfAddressMapByGroup.put(field.getGroup(), hirearchyMap);
					}
//					orderOfAddressMapByGroup.(location.getHierarchyLevel(), matchedfield.get(0).getId());
					LOGGER.info("REGISTRATION - CONTROLLER", APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
							"location.getHierarchyLevel() >>> " + location.getHierarchyLevel()
									+ " matchedfield.get(0).getId() >>> " + matchedfield.get(0).getId());
				}
			}

		}

		for (Entry<String, TreeMap<Integer, String>> entry : orderOfAddressMapByGroup.entrySet()) {

			orderOfAddressListByGroup.put(entry.getKey(),
					entry.getValue().values().stream().collect(Collectors.toList()));

		}
//		orderOfAddress = treeMap.values().stream().collect(Collectors.toList());
	}

	private void disablePreRegFetch() {
		preRegParentPane.setVisible(false);
		preRegParentPane.setManaged(false);
		preRegParentPane.setDisable(true);
	}

//	public GridPane addContent(UiSchemaDTO schemaDTO) {
//		GridPane gridPane = prepareMainGridPane();
//		GridPane primary = subGridPane(schemaDTO, "");
//		gridPane.addColumn(0, primary);
//		if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
//			GridPane secondary = subGridPane(schemaDTO, RegistrationConstants.LOCAL_LANGUAGE);
//
//			gridPane.addColumn(2, secondary);
//		}
//
//		gridPane.setId(schemaDTO.getId() + "ParentGridPane");
//
//		return gridPane;
//	}

	private boolean isAppLangAndLocalLangSame() {

		return primaryLanguage.equals(secondaryLanguage);
	}

	private boolean isLocalLanguageAvailable() {

		return secondaryLanguage != null && !secondaryLanguage.isEmpty();
	}

	public void addKeyboard(int position) {

		if (keyboardVisible) {
			parentFlow.remove(lastPosition);
			keyboardVisible = false;
			lastPosition = position;
		} else {
			GridPane gridPane = prepareMainGridPaneForKeyboard();
			gridPane.addColumn(1, keyboardNode);
			parentFlow.add(position, gridPane);
			lastPosition = position;
			keyboardVisible = true;
		}
	}

	private GridPane prepareMainGridPane() {
		GridPane gridPane = new GridPane();
		gridPane.setPrefWidth(1000);

		ObservableList<ColumnConstraints> columnConstraints = gridPane.getColumnConstraints();
		ColumnConstraints columnConstraint1 = new ColumnConstraints();
		columnConstraint1.setPercentWidth(48);
		ColumnConstraints columnConstraint2 = new ColumnConstraints();
		columnConstraint2.setPercentWidth(7);
		ColumnConstraints columnConstraint3 = new ColumnConstraints();
		columnConstraint3.setPercentWidth(45);
		columnConstraints.addAll(columnConstraint1, columnConstraint2, columnConstraint3);
		return gridPane;
	}

	private GridPane prepareMainGridPaneForKeyboard() {
		GridPane gridPane = new GridPane();
		gridPane.setPrefWidth(1000);

		ObservableList<ColumnConstraints> columnConstraints = gridPane.getColumnConstraints();
		ColumnConstraints columnConstraint1 = new ColumnConstraints();
		columnConstraint1.setPercentWidth(10);
		ColumnConstraints columnConstraint2 = new ColumnConstraints();
		columnConstraint2.setPercentWidth(80);
		ColumnConstraints columnConstraint3 = new ColumnConstraints();
		columnConstraint3.setPercentWidth(10);
		columnConstraints.addAll(columnConstraint1, columnConstraint2, columnConstraint3);
		return gridPane;
	}

	@SuppressWarnings("unlikely-arg-type")
	public GridPane subGridPane(Field schemaDTO, String languageType, int noOfItems) {
		GridPane gridPane = new GridPane();

		ObservableList<ColumnConstraints> columnConstraints = gridPane.getColumnConstraints();
		ColumnConstraints columnConstraint1 = new ColumnConstraints();
		columnConstraint1.setPercentWidth(noOfItems * 5);
		ColumnConstraints columnConstraint2 = new ColumnConstraints();
		columnConstraint2.setPercentWidth(90);
		ColumnConstraints columnConstraint3 = new ColumnConstraints();
		columnConstraint3.setPercentWidth(5);
		columnConstraints.addAll(columnConstraint1, columnConstraint2, columnConstraint3);

		ObservableList<RowConstraints> rowConstraints = gridPane.getRowConstraints();
		RowConstraints rowConstraint1 = new RowConstraints();
//		columnConstraint1.setPercentWidth(10);
		RowConstraints rowConstraint2 = new RowConstraints();
//		columnConstraint1.setPercentWidth(100);
		RowConstraints rowConstraint3 = new RowConstraints();
//		columnConstraint1.setPercentWidth(10);
		rowConstraints.addAll(rowConstraint1, rowConstraint2, rowConstraint3);

		VBox content = null;

		switch (schemaDTO.getControlType()) {
		case RegistrationConstants.DROPDOWN:
			if (schemaDTO.getId().equalsIgnoreCase("gender") || schemaDTO.getId().equalsIgnoreCase("residencestatus")) {
				if (listOfButtons.get(schemaDTO.getId()) == null || listOfButtons.get(schemaDTO.getId()).isEmpty()) {
					populateButtons(schemaDTO.getId());
				}
				if (listOfButtons.get(schemaDTO.getId()) != null && !listOfButtons.get(schemaDTO.getId()).isEmpty()) {
					content = addContentWithButtons(schemaDTO.getId(), schemaDTO, languageType);
				} else {
					content = addContentWithComboBoxObject(schemaDTO.getId(), schemaDTO, languageType);
				}
			} else {
				content = addContentWithComboBoxObject(schemaDTO.getId(), schemaDTO, languageType);
			}
			break;
		case RegistrationConstants.AGE_DATE:
			content = addContentForDobAndAge(schemaDTO.getId(), languageType);
			break;
		case "age":
			// TODO Not yet supported
			break;
		case RegistrationConstants.TEXTBOX:
			content = addContentWithTextField(schemaDTO, schemaDTO.getId(), languageType);
			break;
		}

		gridPane.add(content, 1, 2);

		return gridPane;
	}

	public VBox addContentForDobAndAge(String fieldId, String languageType) {

		VBox vBoxDD = new VBox();

		TextField dd = new TextField();
		dd.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD);
		dd.setId(fieldId + "__" + RegistrationConstants.DD + languageType);
		Label ddLabel = new Label();
		ddLabel.setVisible(false);
		ddLabel.setId(fieldId + "__" + RegistrationConstants.DD + languageType + RegistrationConstants.LABEL);
		ddLabel.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
		vBoxDD.getChildren().addAll(ddLabel, dd);

		listOfTextField.put(fieldId + "__" + RegistrationConstants.DD + languageType, dd);

		VBox vBoxMM = new VBox();
		TextField mm = new TextField();
		mm.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD);
		mm.setId(fieldId + "__" + RegistrationConstants.MM + languageType);
		Label mmLabel = new Label();
		mmLabel.setVisible(false);
		mmLabel.setId(fieldId + "__" + RegistrationConstants.MM + languageType + RegistrationConstants.LABEL);
		mmLabel.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
		vBoxMM.getChildren().addAll(mmLabel, mm);

		listOfTextField.put(fieldId + "__" + RegistrationConstants.MM + languageType, mm);

		VBox vBoxYYYY = new VBox();
		TextField yyyy = new TextField();
		yyyy.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD);
		yyyy.setId(fieldId + "__" + RegistrationConstants.YYYY + languageType);
		Label yyyyLabel = new Label();
		yyyyLabel.setVisible(false);
		yyyyLabel.setId(fieldId + "__" + RegistrationConstants.YYYY + languageType + RegistrationConstants.LABEL);
		yyyyLabel.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
		vBoxYYYY.getChildren().addAll(yyyyLabel, yyyy);

		listOfTextField.put(fieldId + "__" + RegistrationConstants.YYYY + languageType, yyyy);

		Label dobMessage = new Label();
		dobMessage.setId(fieldId + "__" + RegistrationConstants.DOB_MESSAGE + languageType);
		dobMessage.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);

		boolean localLanguage = languageType.equals(RegistrationConstants.LOCAL_LANGUAGE);

		dd.setPromptText(localLanguage ? localLabelBundle.getString(RegistrationConstants.DD)
				: applicationLabelBundle.getString(RegistrationConstants.DD));
		ddLabel.setText(localLanguage ? localLabelBundle.getString(RegistrationConstants.DD)
				: applicationLabelBundle.getString(RegistrationConstants.DD));
		mm.setPromptText(localLanguage ? localLabelBundle.getString(RegistrationConstants.MM)
				: applicationLabelBundle.getString(RegistrationConstants.MM));
		mmLabel.setText(localLanguage ? localLabelBundle.getString(RegistrationConstants.MM)
				: applicationLabelBundle.getString(RegistrationConstants.MM));
		dobMessage.setText("");

		yyyy.setPromptText(localLanguage ? localLabelBundle.getString(RegistrationConstants.YYYY)
				: applicationLabelBundle.getString(RegistrationConstants.YYYY));
		yyyyLabel.setText(localLanguage ? localLabelBundle.getString(RegistrationConstants.YYYY)
				: applicationLabelBundle.getString(RegistrationConstants.YYYY));

		HBox hB = new HBox();
		hB.setSpacing(10);
		hB.getChildren().addAll(vBoxDD, vBoxMM, vBoxYYYY);

		HBox hB2 = new HBox();
		VBox vboxAgeField = new VBox();
		TextField ageField = new TextField();
		ageField.setId(fieldId + "__" + RegistrationConstants.AGE_FIELD + languageType);
		ageField.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD);
		Label ageFieldLabel = new Label();
		ageFieldLabel
				.setId(fieldId + "__" + RegistrationConstants.AGE_FIELD + languageType + RegistrationConstants.LABEL);
		ageFieldLabel.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
		ageFieldLabel.setVisible(false);
		vboxAgeField.getChildren().addAll(ageFieldLabel, ageField);

		listOfTextField.put(RegistrationConstants.AGE_FIELD + languageType, ageField);

		ageField.setPromptText(localLanguage ? localLabelBundle.getString(RegistrationConstants.AGE_FIELD)
				: applicationLabelBundle.getString(RegistrationConstants.AGE_FIELD));
		ageFieldLabel.setText(localLanguage ? localLabelBundle.getString(RegistrationConstants.AGE_FIELD)
				: applicationLabelBundle.getString(RegistrationConstants.AGE_FIELD));

		hB.setPrefWidth(250);

		hB2.setSpacing(10);

		Label orLabel = new Label(localLanguage ? localLabelBundle.getString("ageOrDOBField")
				: applicationLabelBundle.getString("ageOrDOBField"));

		VBox orVbox = new VBox();
		orVbox.setPrefWidth(100);
		orVbox.getChildren().addAll(new Label(), orLabel);

		hB2.getChildren().addAll(hB, orVbox, vboxAgeField);

		VBox vB = new VBox();
		vB.getChildren().addAll(hB2, dobMessage);

		ageBasedOperation(vB, ageField, dobMessage, dd, mm, yyyy);

		fxUtils.focusedAction(hB, dd);
		fxUtils.focusedAction(hB, mm);
		fxUtils.focusedAction(hB, yyyy);

		dateValidation.validateDate(parentFlowPane, dd, mm, yyyy, validation, fxUtils, ageField, null, dobMessage);
		dateValidation.validateMonth(parentFlowPane, dd, mm, yyyy, validation, fxUtils, ageField, null, dobMessage);
		dateValidation.validateYear(parentFlowPane, dd, mm, yyyy, validation, fxUtils, ageField, null, dobMessage);

		vB.setDisable(languageType.equals(RegistrationConstants.LOCAL_LANGUAGE));

		return vB;
	}

	@Autowired
	ResourceLoader resourceLoader;

	private String loggerClassName = "DemographicDetailController";

	private static final String UNDERSCORE = "_";

	private static final String RESIDENCE_STATUS = "residencestatus";

	private static final String GENDER = "gender";

	public VBox addContentWithTextField(Field schema, String fieldName, String languageType) {
		TextField field = new TextField();
		Label label = new Label();
		Label validationMessage = new Label();

		VBox vbox = new VBox();
		vbox.setId(fieldName + RegistrationConstants.Parent);
		field.setId(fieldName + languageType);
		field.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD);
		label.setId(fieldName + languageType + RegistrationConstants.LABEL);
		label.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
		label.setVisible(false);
		validationMessage.setId(fieldName + languageType + RegistrationConstants.MESSAGE);
		validationMessage.getStyleClass().add(RegistrationConstants.DemoGraphicFieldMessageLabel);
		label.setPrefWidth(vbox.getPrefWidth());
		field.setPrefWidth(vbox.getPrefWidth());
		validationMessage.setPrefWidth(vbox.getPrefWidth());
		vbox.setSpacing(5);
		vbox.getChildren().add(label);
		vbox.getChildren().add(field);

		HBox hB = new HBox();
		hB.setSpacing(20);

		vbox.getChildren().add(validationMessage);
//		if (primaryLanguage.equals(secondaryLanguage)) {
//			vbox.setDisable(true);
//		}

		if (listOfTextField.get(fieldName) != null) {
//			fxUtils.populateLocalFieldWithFocus(parentFlowPane, listOfTextField.get(fieldName), field,
//					hasToBeTransliterated, validation);

			setTextFieldListener(listOfTextField.get(fieldName));
		}
		listOfTextField.put(field.getId(), field);

		if (languageType.equals(RegistrationConstants.LOCAL_LANGUAGE)) {
			field.setPromptText(schema.getLabel().get(RegistrationConstants.SECONDARY));
			putIntoLabelMap(fieldName + languageType, schema.getLabel().get(RegistrationConstants.SECONDARY));
			label.setText(schema.getLabel().get(RegistrationConstants.SECONDARY));
			if (!schema.getType().equals(RegistrationConstants.SIMPLE_TYPE)) {
				field.setDisable(true);
			} else {
				ImageView imageView = null;
				try {
					imageView = new ImageView(
							new Image(resourceLoader.getResource("classpath:images/keyboard.png").getInputStream()));
					imageView.setId(fieldName);
					imageView.setFitHeight(20.00);
					imageView.setFitWidth(22.00);
					imageView.setOnMouseClicked((event) -> {
						setFocusonLocalField(event);
					});

					if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
						vk.changeControlOfKeyboard(field);
					}
				} catch (IOException runtimeException) {
					LOGGER.error("keyboard.png image not found in resource folder", APPLICATION_NAME,
							RegistrationConstants.APPLICATION_ID,
							runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));

				}
				hB.getChildren().add(imageView);
			}
		} else {
			field.setPromptText(schema.getLabel().get(RegistrationConstants.PRIMARY));
			putIntoLabelMap(fieldName + languageType, schema.getLabel().get(RegistrationConstants.PRIMARY));
			label.setText(schema.getLabel().get(RegistrationConstants.PRIMARY));
		}

		hB.getChildren().add(validationMessage);
		hB.setStyle("-fx-background-color:WHITE");
		vbox.getChildren().add(hB);

		fxUtils.onTypeFocusUnfocusListener(parentFlowPane, field);
		return vbox;
	}

	private void populateDropDowns() {
		try {
			for (String key : listOfComboBoxWithObject.keySet()) {
				switch (key.toLowerCase()) {
				case GENDER:
					listOfComboBoxWithObject.get(GENDER).getItems()
							.addAll(masterSyncService.getGenderDtls(ApplicationContext.applicationLanguage()).stream()
									.filter(v -> !v.getCode().equals("OTH")).collect(Collectors.toList()));

					if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {

						listOfComboBoxWithObject.get("genderLocalLanguage").getItems()
								.addAll(masterSyncService.getGenderDtls(ApplicationContext.localLanguage()).stream()
										.filter(v -> !v.getCode().equals("OTH")).collect(Collectors.toList()));
					}
					break;

				case RESIDENCE_STATUS:
					listOfComboBoxWithObject.get(RESIDENCE_STATUS).getItems()
							.addAll(masterSyncService.getIndividualType(ApplicationContext.applicationLanguage()));

					if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {

						listOfComboBoxWithObject.get("residenceStatusLocalLanguage").getItems()
								.addAll(masterSyncService.getIndividualType(ApplicationContext.localLanguage()));
					}
					break;

				default:
					if (key.endsWith("LocalLanguage")) {
						if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
							listOfComboBoxWithObject.get(key).getItems().addAll(masterSyncService.getDynamicField(
									key.replace("LocalLanguage", ""), ApplicationContext.localLanguage()));
						}
					} else {
						listOfComboBoxWithObject.get(key).getItems().addAll(
								masterSyncService.getDynamicField(key, ApplicationContext.applicationLanguage()));
					}
					break;
				}
			}
		} catch (RegBaseCheckedException e) {
			LOGGER.error("populateDropDowns", APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					ExceptionUtils.getStackTrace(e));
		}
	}

	public <T> VBox addContentWithComboBoxObject(String fieldName, Field schema, String languageType) {

		ComboBox<GenericDto> field = new ComboBox<GenericDto>();
		Label label = new Label();
		Label validationMessage = new Label();
		StringConverter<T> uiRenderForComboBox = fxUtils.getStringConverterForComboBox();

		VBox vbox = new VBox();
		field.setId(fieldName + languageType);
		field.setPrefWidth(vbox.getPrefWidth());
//		if (listOfComboBoxWithObject.get(fieldName) != null) {
//			fxUtils.populateLocalComboBox(parentFlowPane, listOfComboBoxWithObject.get(fieldName), field);
//		}
		helperMethodForComboBox(field, fieldName, schema, label, validationMessage, vbox, languageType);
		field.setConverter((StringConverter<GenericDto>) uiRenderForComboBox);
		listOfComboBoxWithObject.put(fieldName + languageType, field);

		if (!isLocalLanguageAvailable() || isAppLangAndLocalLangSame()) {
			setComboBoxFieldListener(field, null);
		}
		if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame() && !languageType.isEmpty()) {
			setComboBoxFieldListener(listOfComboBoxWithObject.get(fieldName), field);
		}

		return vbox;
	}

	public <T> VBox addContentWithButtons(String fieldName, Field schema, String languageType) {
		Label label = new Label();
		Label validationMessage = new Label();

		VBox vbox = new VBox();
		vbox.setId(fieldName + RegistrationConstants.Parent);
		if (languageType.equals(RegistrationConstants.LOCAL_LANGUAGE)) {
			label.setText(schema.getLabel().get(RegistrationConstants.SECONDARY));
			putIntoLabelMap(fieldName + languageType, schema.getLabel().get(RegistrationConstants.SECONDARY));
		} else {
			label.setText(schema.getLabel().get(RegistrationConstants.PRIMARY));
			putIntoLabelMap(fieldName + languageType, schema.getLabel().get(RegistrationConstants.PRIMARY));
		}
		vbox.setPrefWidth(500);
		label.setId(fieldName + languageType + RegistrationConstants.LABEL);
		label.setVisible(true);
		label.getStyleClass().add("buttonsLabel");
		validationMessage.setId(fieldName + languageType + RegistrationConstants.MESSAGE);
		validationMessage.getStyleClass().add(RegistrationConstants.DemoGraphicFieldMessageLabel);
		label.setPrefWidth(vbox.getPrefWidth());
		validationMessage.setPrefWidth(vbox.getPrefWidth());
		validationMessage.setVisible(false);

		if (applicationContext.getApplicationLanguage().equals(applicationContext.getLocalLanguage())
				&& languageType.equals(RegistrationConstants.LOCAL_LANGUAGE)) {
			vbox.setDisable(true);
		}

		HBox hBox = new HBox();
		hBox.getChildren().add(label);
		listOfButtons.get(fieldName + languageType).forEach(localButton -> {
			hBox.setId(fieldName + languageType);
			hBox.setSpacing(10);
			hBox.setPadding(new Insets(10, 10, 10, 10));
			localButton.setPrefWidth(vbox.getPrefWidth());
			localButton.getStyleClass().addAll("residence", "button");
//			if (languageType.equals(RegistrationConstants.LOCAL_LANGUAGE)) {
//				button.setDisable(true);
//			} else {
//			if (listOfButtons.get(fieldName) != null) {
//				listOfButtons.get(fieldName).forEach(applicationButton -> {
////					if ((btn.getId().concat(RegistrationConstants.LOCAL_LANGUAGE)).equals(button.getId())) {
//
//					if ((!isLocalLanguageAvailable() || isAppLangAndLocalLangSame())
//							|| (applicationButton.getId().concat(RegistrationConstants.LOCAL_LANGUAGE))
//									.equals(localButton.getId())) {
//						fxUtils.populateLocalButton(parentFlowPane, applicationButton, localButton);
//
//					}
////					}
//				});
//			}
//			}
			hBox.getChildren().add(localButton);
		});
		vbox.getChildren().addAll(hBox, validationMessage);
		return vbox;
	}

	private void populateButtons(String key) {
		try {

			Button primaryButton = null;
			Button secondaryButton = null;

			switch (key.toLowerCase()) {
			case GENDER:
				List<GenericDto> genderAppLanguage = masterSyncService
						.getGenderDtls(ApplicationContext.applicationLanguage()).stream()
						.filter(v -> !v.getCode().equals("OTH")).collect(Collectors.toList());
				List<GenericDto> genderLocalLanguage = masterSyncService
						.getGenderDtls(ApplicationContext.localLanguage()).stream()
						.filter(v -> !v.getCode().equals("OTH")).collect(Collectors.toList());
				if (genderAppLanguage.size() == 2 && genderLocalLanguage.size() == 2) {
					genderAppLanguage.forEach(genericDto -> {
						genderLocalLanguage.forEach(genericDtoLocalLang -> {
							if (genericDto.getCode().equals(genericDtoLocalLang.getCode())) {
								Button button = new Button(genericDto.getName());
								button.setId(key + UNDERSCORE + genericDto.getLangCode() + UNDERSCORE
										+ genericDto.getCode());

								List<Button> buttons = (listOfButtons.get("gender") == null) ? new ArrayList<>()
										: listOfButtons.get("gender");

								// TODO Add a listener to button

								buttons.add(button);
								listOfButtons.put("gender", buttons);

								Button buttonLocalLang = null;
								if (!isAppLangAndLocalLangSame()) {
									buttonLocalLang = new Button(genericDtoLocalLang.getName());

									buttonLocalLang.setId(key + UNDERSCORE + genericDtoLocalLang.getLangCode()
											+ UNDERSCORE + genericDto.getCode());
									List<Button> buttonsLocalLang = (listOfButtons.get("genderLocalLanguage") == null)
											? new ArrayList<>()
											: listOfButtons.get("genderLocalLanguage");
									buttonsLocalLang.add(buttonLocalLang);
									listOfButtons.put("genderLocalLanguage", buttonsLocalLang);
								}

								setButtonFieldListener(button, buttonLocalLang);
							}
						});
					});
				}
				break;

			case "residencestatus":
				List<GenericDto> residenceAppLanguage = (masterSyncService
						.getIndividualType(ApplicationContext.applicationLanguage())).stream()
								.collect(Collectors.toList());
				List<GenericDto> residenceLocalLanguage = (masterSyncService
						.getIndividualType(ApplicationContext.localLanguage())).stream().collect(Collectors.toList());
				if (residenceAppLanguage.size() == 2 && residenceLocalLanguage.size() == 2) {
					residenceAppLanguage.forEach(genericDto -> {
						residenceLocalLanguage.forEach(genericDtoLocalLang -> {
							if (genericDto.getCode().equals(genericDtoLocalLang.getCode())) {
								Button button = new Button(genericDto.getName());

								button.setId(key + UNDERSCORE + genericDto.getLangCode() + UNDERSCORE
										+ genericDto.getCode());
								List<Button> buttons = (listOfButtons.get("residenceStatus") == null)
										? new ArrayList<>()
										: listOfButtons.get("residenceStatus");
								buttons.add(button);
								listOfButtons.put("residenceStatus", buttons);

								Button buttonLocalLang = null;
								if (!isAppLangAndLocalLangSame()) {
									buttonLocalLang = new Button(genericDtoLocalLang.getName());

									buttonLocalLang.setId(key + UNDERSCORE + genericDtoLocalLang.getLangCode()
											+ UNDERSCORE + genericDtoLocalLang.getCode());
									List<Button> buttonsLocalLang = (listOfButtons
											.get("residenceStatusLocalLanguage") == null) ? new ArrayList<>()
													: listOfButtons.get("residenceStatusLocalLanguage");
									buttonsLocalLang.add(buttonLocalLang);
									listOfButtons.put("residenceStatusLocalLanguage", buttonsLocalLang);
								}

								setButtonFieldListener(button, buttonLocalLang);
							}
						});
					});
				}
				break;

			default:
				// TODO
				break;
			}

		} catch (RegBaseCheckedException e) {
			LOGGER.error("populateDropDowns", APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					ExceptionUtils.getStackTrace(e));
		}
	}

	private void setButtonFieldListener(Button primaryButton, Button secondaryButton) {
//		1. style class set,,,local also

		primaryButton.addEventHandler(ActionEvent.ACTION, event -> {

			if (primaryButton.getStyleClass().contains("residence")) {
				primaryButton.getStyleClass().clear();

				/** Pink colour Ui style class "selectedResidence", "button" */
				/** White colour Ui style class "residence", "button" */
				primaryButton.getStyleClass().addAll("selectedResidence", "button");
				primaryButton.getParent().getChildrenUnmodifiable().forEach(node -> {
					if (node instanceof Button && !node.getId().equals(primaryButton.getId())) {
						node.getStyleClass().clear();
						node.getStyleClass().addAll("residence", "button");
					}
				});

				if (secondaryButton != null) {
					secondaryButton.getStyleClass().clear();
					secondaryButton.getStyleClass().addAll("selectedResidence", "button");

					secondaryButton.getParent().getChildrenUnmodifiable().forEach(node -> {
						if (node instanceof Button && !node.getId().equals(secondaryButton.getId())) {
							node.getStyleClass().clear();
							node.getStyleClass().addAll("residence", "button");
						}
					});
				}
			}

//			3. label/error message
			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Show error Label for primary button Field : " + false);

			fxUtils.toggleUIField(parentFlowPane, primaryButton.getParent().getId() + RegistrationConstants.MESSAGE,
					false);

			if (secondaryButton != null) {

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Show error Label for secondary button Field : " + false);

				fxUtils.toggleUIField(parentFlowPane,
						secondaryButton.getParent().getId() + RegistrationConstants.MESSAGE, false);
			}

//			4. button val ,,add to session

			RegistrationDTO registrationDTO = getRegistrationDTOFromSession();

			String[] buttonIdVal = primaryButton.getId().split(UNDERSCORE);

			if (buttonIdVal != null && registrationDTO != null) {

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Add button value to session");
				registrationDTO.addDemographicField(buttonIdVal[0], applicationContext.getApplicationLanguage(),
						primaryButton.getText(), applicationContext.getLocalLanguage(),
						secondaryButton != null ? secondaryButton.getText() : null);

//				5. refresh 
				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Refresh demographics");
				refreshDemographicGroups(registrationDTO.getMVELDataContext());
			}

		});

	}

	/**
	 * setting the registration navigation label to lost uin
	 */
	protected void lostUIN() {
		lostUIN = true;
		registrationNavlabel
				.setText(ApplicationContext.applicationLanguageBundle().getString(RegistrationConstants.LOSTUINLBL));
	}

	/**
	 * To restrict the user not to eavcdnter any values other than integer values.
	 */
	private void ageBasedOperation(Pane parentPane, TextField ageField, Label dobMessage, TextField dd, TextField mm,
			TextField yyyy) {
		try {
			LOGGER.debug(RegistrationConstants.REGISTRATION_CONTROLLER, APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Validating the age given by age field");
			fxUtils.validateLabelFocusOut(parentPane, ageField);
			ageField.focusedProperty().addListener((obsValue, oldValue, newValue) -> {
				if (!getAge(yyyy.getText(), mm.getText(), dd.getText()).equals(ageField.getText())) {
					if (oldValue) {
						ageValidation(parentPane, ageField, dobMessage, oldValue, dd, mm, yyyy);
					}
				}
			});
			LOGGER.debug(RegistrationConstants.REGISTRATION_CONTROLLER, APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Validating the age given by age field");
		} catch (RuntimeException runtimeException) {
			LOGGER.error("REGISTRATION - AGE FIELD VALIDATION FAILED ", APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
		}
	}

	/**
	 * Gets the age.
	 *
	 * @param year  the year
	 * @param month the month
	 * @param date  the date
	 * @return the age
	 */
	String getAge(String year, String month, String date) {
		if (year != null && !year.isEmpty() && month != null && !month.isEmpty() && date != null && !date.isEmpty()) {
			LocalDate birthdate = new LocalDate(Integer.parseInt(year), Integer.parseInt(month),
					Integer.parseInt(date)); // Birth
												// date
			LocalDate now = new LocalDate(); // Today's date

			Period period = new Period(birthdate, now, PeriodType.yearMonthDay());
			return String.valueOf(period.getYears());
		} else {
			return "";
		}

	}

	public void ageValidation(Pane dobParentPane, TextField ageField, Label dobMessage, Boolean oldValue, TextField dd,
			TextField mm, TextField yyyy) {
		if (ageField.getText().matches(RegistrationConstants.NUMBER_OR_NOTHING_REGEX)) {
			if (ageField.getText().matches(RegistrationConstants.NUMBER_REGEX)) {
				if (maxAge >= Integer.parseInt(ageField.getText())) {
					age = Integer.parseInt(ageField.getText());

					// Not to re-calulate DOB and populate DD, MM and YYYY UI fields based on Age,
					// since Age was calculated based on DOB entered by the user. Calculate DOB and
					// populate DD, MM and YYYY UI fields based on user entered Age.
					Calendar defaultDate = Calendar.getInstance();
					defaultDate.set(Calendar.DATE, 1);
					defaultDate.set(Calendar.MONTH, 0);
					defaultDate.add(Calendar.YEAR, -age);

					dd.setText(String.valueOf(defaultDate.get(Calendar.DATE)));

					dd.requestFocus();
					mm.setText(String.valueOf(defaultDate.get(Calendar.MONTH + 1)));
					mm.requestFocus();
					yyyy.setText(String.valueOf(defaultDate.get(Calendar.YEAR)));

					yyyy.requestFocus();
					dd.requestFocus();

					if (getFxElement(dd.getId() + RegistrationConstants.LOCAL_LANGUAGE) != null) {
						((TextField) getFxElement(dd.getId() + RegistrationConstants.LOCAL_LANGUAGE))
								.setText(dd.getText());

					}

					if (getFxElement(yyyy.getId() + RegistrationConstants.LOCAL_LANGUAGE) != null) {
						((TextField) getFxElement(yyyy.getId() + RegistrationConstants.LOCAL_LANGUAGE))
								.setText(yyyy.getText());

					}

					if (getFxElement(mm.getId() + RegistrationConstants.LOCAL_LANGUAGE) != null) {
						((TextField) getFxElement(mm.getId() + RegistrationConstants.LOCAL_LANGUAGE))
								.setText(mm.getText());

					}

					if (getFxElement(ageField.getId() + RegistrationConstants.LOCAL_LANGUAGE) != null) {
						((TextField) getFxElement(ageField.getId() + RegistrationConstants.LOCAL_LANGUAGE))
								.setText(ageField.getText());
					}

					getRegistrationDTOFromSession().setDateField(null, dd.getText(), mm.getText(), yyyy.getText());
					refreshDemographicGroups(getRegistrationDTOFromSession().getMVELDataContext());
					fxUtils.validateOnFocusOut(dobParentPane, ageField, validation, false);
				} else {
					ageField.getStyleClass().remove("demoGraphicTextFieldOnType");
					ageField.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD_FOCUSED);
					Label ageFieldLabel = (Label) dobParentPane
							.lookup("#" + ageField.getId() + RegistrationConstants.LABEL);
					ageFieldLabel.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
					ageField.getStyleClass().remove("demoGraphicFieldLabelOnType");
					dobMessage.setText(RegistrationUIConstants.INVALID_AGE + maxAge);
					dobMessage.setVisible(true);

					generateAlert(dobParentPane, RegistrationConstants.DOB, dobMessage.getText());
					// parentFieldValidation();
				}
			} else {
				// updatePageFlow(RegistrationConstants.GUARDIAN_BIOMETRIC, false);
				// updatePageFlow(RegistrationConstants.FINGERPRINT_CAPTURE, true);
				// updatePageFlow(RegistrationConstants.IRIS_CAPTURE, true);

				dd.clear();
				mm.clear();
				yyyy.clear();
			}
		} else {

			ageField.setText(RegistrationConstants.EMPTY);
		}
	}

	private void addFirstOrderAddress(ComboBox<GenericDto> location, int id, String languageType) {
		if (location != null) {
			location.getItems().clear();
			try {
				List<GenericDto> locations = null;
				locations = masterSync.findLocationByHierarchyCode(id, languageType);

				if (locations.isEmpty()) {
					GenericDto lC = new GenericDto();
					lC.setCode(RegistrationConstants.AUDIT_DEFAULT_USER);
					lC.setName(RegistrationConstants.AUDIT_DEFAULT_USER);
					lC.setLangCode(ApplicationContext.applicationLanguage());
					location.getItems().add(lC);
				} else {
					location.getItems().addAll(locations);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private List<GenericDto> LocationDtoToComboBoxDto(List<LocationDto> locations) {
		List<GenericDto> listOfValues = new ArrayList<>();
		for (LocationDto locationDto : locations) {
			GenericDto comboBox = new GenericDto();
			comboBox.setCode(locationDto.getCode());
			comboBox.setName(locationDto.getName());
			comboBox.setLangCode(locationDto.getLangCode());
			listOfValues.add(comboBox);
		}
		return listOfValues;
	}

	private void addDemoGraphicDetailsToSession() {
		try {
			RegistrationDTO registrationDTO = getRegistrationDTOFromSession();
			for (Field schemaField : getUiSchemaFieldMap().values()) {
				if (schemaField.getControlType() == null)
					continue;

				if (registrationDTO.getDefaultUpdatableFields() != null
						&& registrationDTO.getDefaultUpdatableFields().contains(schemaField.getId())) {

					addTextFieldToSession(schemaField.getId(), registrationDTO, true);
				}
				if (registrationDTO.getRegistrationCategory().equals(RegistrationConstants.PACKET_TYPE_UPDATE)
						&& !registrationDTO.getUpdatableFields().contains(schemaField.getId()))
					continue;

				switch (schemaField.getType()) {
				case RegistrationConstants.SIMPLE_TYPE:
					if (schemaField.getControlType().equals(RegistrationConstants.DROPDOWN)) {
						ComboBox<GenericDto> platformField = listOfComboBoxWithObject.get(schemaField.getId());
						ComboBox<GenericDto> localField = listOfComboBoxWithObject
								.get(schemaField.getId() + RegistrationConstants.LOCAL_LANGUAGE);
						registrationDTO.addDemographicField(schemaField.getId(),
								applicationContext.getApplicationLanguage(),
								platformField == null ? null
										: platformField.getValue() != null ? platformField.getValue().getName() : null,
								applicationContext.getLocalLanguage(), localField == null ? null
										: localField.getValue() != null ? localField.getValue().getName() : null);
						if (schemaField.getId().equalsIgnoreCase("gender")
								|| schemaField.getId().equalsIgnoreCase("residencestatus")) {
							List<Button> platformFieldButtons = listOfButtons.get(schemaField.getId());
							List<Button> localFieldButtons = listOfButtons
									.get(schemaField.getId() + RegistrationConstants.LOCAL_LANGUAGE);
							AtomicReference<Boolean> buttonSelected = new AtomicReference<>(false);

							if (platformFieldButtons != null) {
								platformFieldButtons.forEach(button -> {
									if (button.getStyleClass().contains("selectedResidence")) {
										buttonSelected.set(true);
										registrationDTO.addDemographicField(schemaField.getId(),
												applicationContext.getApplicationLanguage(), button.getText(),
												applicationContext.getLocalLanguage(),
												getLocalFieldButtonText(button, localFieldButtons));
									}
								});
								if (!buttonSelected.get()) {
									registrationDTO.addDemographicField(schemaField.getId(),
											applicationContext.getApplicationLanguage(), null,
											applicationContext.getLocalLanguage(), null);
								}
							}
						}
					} else {

						addTextFieldToSession(schemaField.getId(), registrationDTO, false);

					}
					break;
				case RegistrationConstants.NUMBER:
				case RegistrationConstants.STRING:
					if (schemaField.getControlType().equalsIgnoreCase(RegistrationConstants.AGE_DATE))
						registrationDTO.setDateField(schemaField.getId(),
								listOfTextField.get(schemaField.getId() + "__" + RegistrationConstants.DD).getText(),
								listOfTextField.get(schemaField.getId() + "__" + RegistrationConstants.MM).getText(),
								listOfTextField.get(schemaField.getId() + "__" + RegistrationConstants.YYYY).getText());
					else {
						if (schemaField.getControlType().equals(RegistrationConstants.DROPDOWN)) {
							ComboBox<GenericDto> platformField = listOfComboBoxWithObject.get(schemaField.getId());
							registrationDTO.addDemographicField(schemaField.getId(), platformField == null ? null
									: platformField.getValue() != null ? platformField.getValue().getName() : null);
						} else {
							TextField platformField = listOfTextField.get(schemaField.getId());
							registrationDTO.addDemographicField(schemaField.getId(),
									platformField != null ? platformField.getText() : null);
						}
					}
					break;

				default:
					break;
				}
			}
		} catch (Exception exception) {
			LOGGER.error("addDemoGraphicDetailsToSession", APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));
		}
	}

	private void addTextFieldToSession(String id, RegistrationDTO registrationDTO, boolean isDefaultField) {

		if (registrationDTO != null) {
			TextField platformField = listOfTextField.get(id);
			TextField localField = listOfTextField.get(id + RegistrationConstants.LOCAL_LANGUAGE);
			if (!isDefaultField) {
				registrationDTO.addDemographicField(id, applicationContext.getApplicationLanguage(),
						platformField.getText(), applicationContext.getLocalLanguage(),
						localField == null ? null : localField.getText());
			} else {
				registrationDTO.addDefaultDemographicField(id, applicationContext.getApplicationLanguage(),
						platformField.getText(), applicationContext.getLocalLanguage(),
						localField == null ? null : localField.getText());
			}
		}
	}

	private String getLocalFieldButtonText(Button button, List<Button> localFieldButtons) {
		if (!isLocalLanguageAvailable() || isAppLangAndLocalLangSame()) {
			return null;
		}
		String buttonText = null;
		Optional<Button> localButton = localFieldButtons.stream().filter(btn -> btn.getId().contains(button.getId()))
				.findAny();
		if (localButton.isPresent()) {
			buttonText = localButton.get().getText();
		}
		return buttonText;
	}

	/**
	 * To load the provinces in the selection list based on the language code
	 */
	private void configureMethodsForAddress(int s, int p, int size, List<String> orderOfAddress) {
		try {
			retrieveAndPopulateLocationByHierarchy(listOfComboBoxWithObject.get(orderOfAddress.get(s)),
					listOfComboBoxWithObject.get(orderOfAddress.get(p)),
					listOfComboBoxWithObject.get(orderOfAddress.get(p) + RegistrationConstants.LOCAL_LANGUAGE));

			for (int i = p + 1; i < size; i++) {
				listOfComboBoxWithObject.get(orderOfAddress.get(i)).getItems().clear();

				if (!isAppLangAndLocalLangSame()) {
					listOfComboBoxWithObject.get(orderOfAddress.get(i) + RegistrationConstants.LOCAL_LANGUAGE)
							.getItems().clear();
				}
			}
		} catch (RuntimeException runtimeException) {
			LOGGER.error(" falied due to invalid field", APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
		}

	}

	/**
	 * 
	 * Saving the detail into concerned DTO'S
	 * 
	 */
	public void saveDetail() {
		LOGGER.debug(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Saving the fields to DTO");
		try {
			auditFactory.audit(AuditEvent.SAVE_DETAIL_TO_DTO, Components.REGISTRATION_CONTROLLER,
					SessionContext.userContext().getUserId(), RegistrationConstants.ONBOARD_DEVICES_REF_ID_TYPE);

			RegistrationDTO registrationDTO = getRegistrationDTOFromSession();
			/*
			 * if (preRegistrationId.getText() == null &&
			 * preRegistrationId.getText().isEmpty()) {
			 * registrationDTO.setPreRegistrationId(""); }
			 */

			addDemoGraphicDetailsToSession();

			SessionContext.map().put(RegistrationConstants.IS_Child, registrationDTO.isChild());

			registrationDTO.getOsiDataDTO().setOperatorID(SessionContext.userContext().getUserId());

			LOGGER.debug(RegistrationConstants.REGISTRATION_CONTROLLER, APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Saved the demographic fields to DTO");

		} catch (Exception exception) {
			LOGGER.error("REGISTRATION - SAVING THE DETAILS FAILED ", APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));
		}
	}

	public void uinUpdate() {
		List<String> selectionList = getRegistrationDTOFromSession().getUpdatableFields();
		if (selectionList != null) {
			disablePreRegFetch();
			registrationNavlabel.setText(applicationLabelBundle.getString("uinUpdateNavLbl"));

//			for (Node pane : parentFlowPane.getChildren()) {
//
//				pane.setDisable(true);
//
//			}
		}
		for (Entry<String, Field> selectionField : getUiSchemaFieldMap().entrySet()) {

			updateDemographicScreen(selectionField.getKey(), selectionList, false);
			updateDemographicScreen(selectionField.getKey() + RegistrationConstants.LOCAL_LANGUAGE, selectionList,
					false);

//			updateDemographicScreen(selectionField.getKey(),
//					getRegistrationDTOFromSession().getDefaultUpdatableFields());

			if (getRegistrationDTOFromSession().getDefaultUpdatableFields().contains(selectionField.getKey())) {
				updateDemographicScreen(selectionField.getKey(), selectionList, true);
				updateDemographicScreen(selectionField.getKey() + RegistrationConstants.LOCAL_LANGUAGE, selectionList,
						true);

			}
		}

	}

	private void updateDemographicScreen(String key, List<String> selectionList, boolean isDefault) {

		if (getFxElement(key) != null) {
			if (getFxElement(key).getParent() != null) {

				boolean isDisable = true;

				if (selectionList.contains(key.replaceAll(RegistrationConstants.LOCAL_LANGUAGE, "")) || isDefault) {
					isDisable = false;
				}

				getFxElement(key).getParent().getParent().setDisable(isDisable);

			}
		}
	}

	/**
	 * This method is to prepopulate all the values for edit operation
	 */
	public void prepareEditPageContent() {
		try {
			LOGGER.debug(RegistrationConstants.REGISTRATION_CONTROLLER, APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Preparing the Edit page content");

			RegistrationDTO registrationDTO = getRegistrationDTOFromSession();
			Map<String, Object> demographics = registrationDTO.getDemographics();

			for (Field schemaField : getUiSchemaFieldMap().values()) {
				Object value = demographics.get(schemaField.getId());
				if (value == null)
					continue;

				switch (schemaField.getType()) {
				case RegistrationConstants.SIMPLE_TYPE:
					if (schemaField.getControlType().equals(RegistrationConstants.DROPDOWN)
							|| isLocationField(schemaField.getId())) {
						populateFieldValue(listOfComboBoxWithObject.get(schemaField.getId()),
								listOfComboBoxWithObject
										.get(schemaField.getId() + RegistrationConstants.LOCAL_LANGUAGE),
								(List<SimpleDto>) value);
					} else
						populateFieldValue(listOfTextField.get(schemaField.getId()),
								listOfTextField.get(schemaField.getId() + RegistrationConstants.LOCAL_LANGUAGE),
								(List<SimpleDto>) value);
					break;

				case RegistrationConstants.NUMBER:
				case RegistrationConstants.STRING:
					if (RegistrationConstants.AGE_DATE.equalsIgnoreCase(schemaField.getControlType())) {
						String[] dateParts = ((String) value).split("/");
						if (dateParts.length == 3) {
							listOfTextField.get(schemaField.getId() + "__" + "dd").setText(dateParts[2]);
							listOfTextField.get(schemaField.getId() + "__" + "mm").setText(dateParts[1]);
							listOfTextField.get(schemaField.getId() + "__" + "yyyy").setText(dateParts[0]);
						}
					} else if (RegistrationConstants.DROPDOWN.equalsIgnoreCase(schemaField.getControlType())
							|| isLocationField(schemaField.getId())) {
						ComboBox<GenericDto> platformField = listOfComboBoxWithObject.get(schemaField.getId());
						if (platformField != null) {
							platformField.setValue(new GenericDto((String) value, (String) value, "eng"));
						}
					} else {
						TextField textField = listOfTextField.get(schemaField.getId());
						if (textField != null)
							textField.setText((String) value);
					}
				}
			}

			/*
			 * if (SessionContext.map().get(RegistrationConstants.IS_Child) != null) {
			 * boolean isChild = (boolean)
			 * SessionContext.map().get(RegistrationConstants.IS_Child);
			 * parentDetailPane.setDisable(!isChild); parentDetailPane.setVisible(isChild);
			 * }
			 */

			preRegistrationId.setText(registrationDTO.getPreRegistrationId());

		} catch (RuntimeException runtimeException) {
			LOGGER.error(RegistrationConstants.REGISTRATION_CONTROLLER, APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
		}

	}

	/**
	 * Method to populate the local field value
	 *
	 */
	private void populateFieldValue(Node nodeForPlatformLang, Node nodeForLocalLang, List<SimpleDto> fieldValues) {
		if (fieldValues != null) {
			String platformLanguageCode = applicationContext.getApplicationLanguage();
			String localLanguageCode = applicationContext.getLocalLanguage();
			String valueInPlatformLang = RegistrationConstants.EMPTY;
			String valueinLocalLang = RegistrationConstants.EMPTY;

			for (SimpleDto fieldValue : fieldValues) {
				if (fieldValue.getLanguage().equalsIgnoreCase(platformLanguageCode)) {
					valueInPlatformLang = fieldValue.getValue();
				} else if (nodeForLocalLang != null && fieldValue.getLanguage().equalsIgnoreCase(localLanguageCode)) {
					valueinLocalLang = fieldValue.getValue();
				}
			}

			if (nodeForPlatformLang instanceof TextField) {
				((TextField) nodeForPlatformLang).setText(valueInPlatformLang);

				if (nodeForLocalLang != null) {
					((TextField) nodeForLocalLang).setText(valueinLocalLang);
				}
			} else if (nodeForPlatformLang instanceof ComboBox) {
				fxUtils.selectComboBoxValue((ComboBox<?>) nodeForPlatformLang, valueInPlatformLang);
			}
		}
	}

	/**
	 * Method to fetch the pre-Registration details
	 */
	@FXML
	private void fetchPreRegistration() {

		String preRegId = preRegistrationId.getText();

		if (StringUtils.isEmpty(preRegId)) {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.PRE_REG_ID_EMPTY);
			return;
		} else {
			try {
				pridValidatorImpl.validateId(preRegId);
			} catch (InvalidIDException invalidIDException) {
				generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.PRE_REG_ID_NOT_VALID);
				LOGGER.error("PRID VALIDATION FAILED", APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						invalidIDException.getMessage() + ExceptionUtils.getStackTrace(invalidIDException));
				return;
			}
		}

		auditFactory.audit(AuditEvent.REG_DEMO_PRE_REG_DATA_FETCH, Components.REG_DEMO_DETAILS, SessionContext.userId(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		registrationController.createRegistrationDTOObject(RegistrationConstants.PACKET_TYPE_NEW);
		documentScanController.clearDocSection();

		ResponseDTO responseDTO = preRegistrationDataSyncService.getPreRegistration(preRegId);

		SuccessResponseDTO successResponseDTO = responseDTO.getSuccessResponseDTO();
		List<ErrorResponseDTO> errorResponseDTOList = responseDTO.getErrorResponseDTOs();

		if (successResponseDTO != null && successResponseDTO.getOtherAttributes() != null
				&& successResponseDTO.getOtherAttributes().containsKey(RegistrationConstants.REGISTRATION_DTO)) {
			SessionContext.map().put(RegistrationConstants.REGISTRATION_DATA,
					successResponseDTO.getOtherAttributes().get(RegistrationConstants.REGISTRATION_DTO));
			getRegistrationDTOFromSession().setPreRegistrationId(preRegId);
			prepareEditPageContent();

		} else if (errorResponseDTOList != null && !errorResponseDTOList.isEmpty()) {
			generateAlertLanguageSpecific(RegistrationConstants.ERROR, errorResponseDTOList.get(0).getMessage());
		}
	}

	/**
	 * 
	 * Setting the focus to specific fields when keyboard loads
	 * 
	 */
	private void setFocusonLocalField(MouseEvent event) {
		try {
			Node node = (Node) event.getSource();
			if (!isAppLangAndLocalLangSame()) {
				listOfTextField.get(node.getId() + "LocalLanguage").requestFocus();
			}

			if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
				keyboardNode.setVisible(true);
				keyboardNode.setManaged(true);
			}
			addKeyboard(positionTracker.get((node.getId() + "ParentGridPane")) + 1);

		} catch (RuntimeException runtimeException) {
			LOGGER.error("REGISTRATION - SETTING FOCUS ON LOCAL FIELD FAILED", APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
		}
	}

	/**
	 * Method to go back to previous page
	 */
	@FXML
	private void back() {
		try {

			// TODO add back button feature
			if (getRegistrationDTOFromSession().getSelectionListDTO() != null) {
				Parent uinUpdate = BaseController.load(getClass().getResource(RegistrationConstants.UIN_UPDATE));
				getScene(uinUpdate);
			} else {
				goToHomePageFromRegistration();
			}
		} catch (IOException exception) {
			LOGGER.error("COULD NOT LOAD HOME PAGE", APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));
		}
	}

	/**
	 * Method to go back to next page
	 */
	@FXML
	private void next() throws InvalidApplicantArgumentException, ParseException {

		if (preRegistrationId.getText().isEmpty()) {
			preRegistrationId.clear();
		}

		// Its required to save before validation as on spot check for values during
		// MVEL validation
		saveDetail();

		if (registrationController.validateDemographicPane(parentFlowPane)) {
			// saveDetail();

			guardianBiometricsController.populateBiometricPage(false, false);
			/*
			 * SessionContext.map().put("demographicDetail", false);
			 * SessionContext.map().put("documentScan", true);
			 */

			documentScanController.populateDocumentCategories();

			auditFactory.audit(AuditEvent.REG_DEMO_NEXT, Components.REG_DEMO_DETAILS, SessionContext.userId(),
					AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			String prevScreen = pageFlow.getCurrentScreenName();

			pageFlow.updateNext();
			registrationController.showCurrentPage(prevScreen, pageFlow.getNextScreenName());

//			
//			registrationController.showCurrentPage(RegistrationConstants.DEMOGRAPHIC_DETAIL,
//					getPageByAction(RegistrationConstants.DEMOGRAPHIC_DETAIL, RegistrationConstants.NEXT));

		}
	}

	/**
	 * Retrieving and populating the location by hierarchy
	 */
	private void retrieveAndPopulateLocationByHierarchy(ComboBox<GenericDto> srcLocationHierarchy,
			ComboBox<GenericDto> destLocationHierarchy, ComboBox<GenericDto> destLocationHierarchyInLocal) {
		LOGGER.info("REGISTRATION - INDIVIDUAL_REGISTRATION - RETRIEVE_AND_POPULATE_LOCATION_BY_HIERARCHY",
				RegistrationConstants.APPLICATION_ID, RegistrationConstants.APPLICATION_NAME,
				"Retrieving and populating of location by selected hirerachy started");

		try {

			GenericDto selectedLocationHierarchy = srcLocationHierarchy.getSelectionModel().getSelectedItem();
			if (selectedLocationHierarchy != null) {
				destLocationHierarchy.getItems().clear();

				if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
					destLocationHierarchyInLocal.getItems().clear();
				}
				if (selectedLocationHierarchy.getCode().equalsIgnoreCase(RegistrationConstants.AUDIT_DEFAULT_USER)) {
					destLocationHierarchy.getItems().add(selectedLocationHierarchy);

					if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
						destLocationHierarchyInLocal.getItems().add(selectedLocationHierarchy);
					}
				} else {

					List<GenericDto> locations = masterSync.findProvianceByHierarchyCode(
							selectedLocationHierarchy.getCode(), selectedLocationHierarchy.getLangCode());

					if (locations.isEmpty()) {
						GenericDto lC = new GenericDto();
						lC.setCode(RegistrationConstants.AUDIT_DEFAULT_USER);
						lC.setName(RegistrationConstants.AUDIT_DEFAULT_USER);
						lC.setLangCode(ApplicationContext.applicationLanguage());
						destLocationHierarchy.getItems().add(lC);

						if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
							destLocationHierarchyInLocal.getItems().add(lC);
						}
					} else {
						destLocationHierarchy.getItems().addAll(locations);
					}
					if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
						List<GenericDto> locationsSecondary = masterSync.findProvianceByHierarchyCode(
								selectedLocationHierarchy.getCode(), ApplicationContext.localLanguage());

						if (locationsSecondary.isEmpty()) {
							GenericDto lC = new GenericDto();
							lC.setCode(RegistrationConstants.AUDIT_DEFAULT_USER);
							lC.setName(RegistrationConstants.AUDIT_DEFAULT_USER);
							lC.setLangCode(ApplicationContext.localLanguage());
							destLocationHierarchyInLocal.getItems().add(lC);
						} else {
							destLocationHierarchyInLocal.getItems().addAll(locationsSecondary);
						}
					}
				}
			}
		} catch (RuntimeException | RegBaseCheckedException runtimeException) {
			LOGGER.error("REGISTRATION - INDIVIDUAL_REGISTRATION - RETRIEVE_AND_POPULATE_LOCATION_BY_HIERARCHY",
					APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
		}
		LOGGER.info("REGISTRATION - INDIVIDUAL_REGISTRATION - RETRIEVE_AND_POPULATE_LOCATION_BY_HIERARCHY",
				RegistrationConstants.APPLICATION_ID, RegistrationConstants.APPLICATION_NAME,
				"Retrieving and populating of location by selected hirerachy ended");
	}

	private boolean isLocationField(String fieldId) {
		boolean isLocationField = false;
		for (Entry<String, List<String>> entry : orderOfAddressListByGroup.entrySet()) {
			if (entry.getValue().contains(fieldId)) {
				isLocationField = true;
				break;
			}

		}
		return isLocationField;

	}

	private void addGroupContent(List<Field> fields, GridPane groupGridPane) {

		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID, "Adding group contents");

		GridPane horizontalRowGridPane = null;
		if (fields != null && !fields.isEmpty()) {

			if (fields.size() > 0) {
				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Requesting prepare grid pane for horizontal layout");

				horizontalRowGridPane = prepareRowGridPane(fields.size());
			}

			for (int index = 0; index < fields.size(); index++) {

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Adding ui schema of application language");

//				boolean isLeftSpaceRequired = true;
//				if (uiSchemaDTOs.size() > 1) {
//					isLeftSpaceRequired = false;
//				}
				GridPane applicationLanguageGridPane = subGridPane(fields.get(index), "", fields.size());

//				applicationLanguageGridPane.setId(uiSchemaDTOs.get(index).getId());

				GridPane rowGridPane = horizontalRowGridPane == null ? prepareRowGridPane(1) : horizontalRowGridPane;

				rowGridPane.addColumn(horizontalRowGridPane == null ? 0 : index, applicationLanguageGridPane);
				if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {

					LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
							"Adding ui schema of local language");

					GridPane localLanguageGridPane = subGridPane(fields.get(index),
							RegistrationConstants.LOCAL_LANGUAGE,
							fields.size() > 1 ? fields.size() + 4 : fields.size());

					if (localLanguageGridPane != null) {

//						localLanguageGridPane
//								.setId(uiSchemaDTOs.get(index).getId() + RegistrationConstants.LOCAL_LANGUAGE);

						rowGridPane.addColumn(horizontalRowGridPane == null ? 2 : fields.size() + index,
								localLanguageGridPane);
					}
				}

				if (horizontalRowGridPane == null) {
					LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
							"Setting vertical row gridpane for : " + fields.get(index).getId());
					groupGridPane.getChildren().add(rowGridPane);
				}

			}

		}

		if (horizontalRowGridPane != null) {
			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Setting horizontal row gridpane for group : ");
			groupGridPane.getChildren().add(horizontalRowGridPane);
		}
	}

	private GridPane prepareRowGridPane(int noOfItems) {
		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Preparing grid pane for items : " + noOfItems);
		GridPane gridPane = new GridPane();
		gridPane.setPrefWidth(1200);
		ObservableList<ColumnConstraints> columnConstraints = gridPane.getColumnConstraints();

		if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {
			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Preparing grid pane for items : " + noOfItems + " left hand side");

			// Left Hand Side
			setColumnConstraints(columnConstraints, 50, noOfItems);
//		LOGGER.debug(RegistrationConstants.REGISTRATION_CONTROLLER, APPLICATION_NAME,
//				RegistrationConstants.APPLICATION_ID, "Preparing grid pane space in middle");
//		// Middle Space
//			setColumnConstraints(columnConstraints, 10, 1);
			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Preparing grid pane for items : " + noOfItems + " right hand side");
			// Right Hand Side
			setColumnConstraints(columnConstraints, 50, noOfItems);

//			// Right most gap space
//			setColumnConstraints(columnConstraints, 10, 1);
		} else {
			setColumnConstraints(columnConstraints, 100, noOfItems);
		}
		return gridPane;
	}

	private void setColumnConstraints(ObservableList<ColumnConstraints> columnConstraints, int currentPaneWidth,
			int noOfItems) {

		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Setting column constraints");

		if (columnConstraints != null) {
			for (int index = 1; index <= noOfItems; ++index) {

				ColumnConstraints columnConstraint = new ColumnConstraints();
				columnConstraint.setPercentWidth(currentPaneWidth / noOfItems);
				columnConstraints.add(columnConstraint);

			}
		}
	}

	private void setTextFieldListener(TextField textField) {

		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Setting Text Field Listener");

		textField.focusedProperty().addListener((observable, oldValue, newValue) -> {

			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"text field listener started : " + textField.getId());

			if (!textField.isFocused()) {

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"text field not in focused now");

				TextField localField = (TextField) getFxElement(
						textField.getId() + RegistrationConstants.LOCAL_LANGUAGE);

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Show text field label");

				fxUtils.showLabel(parentFlowPane, textField);
				if (localField != null) {

					LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
							"Setting secondary langauge text");

					// Set Local lang
					setSecondaryLangText(textField, localField, hasToBeTransliterated);

					LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
							"showing secondary label");

					fxUtils.showLabel(parentFlowPane, localField);

				}

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"validating text field primary");

				boolean isPrimaryFieldValid = isInputTextValid(textField, textField.getId());

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"validating text field secondary");

				boolean isLocalFieldValid = isInputTextValid(localField, localField.getId());

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Text field primary field valid : " + isPrimaryFieldValid + " and secondary field valid : "
								+ isLocalFieldValid);
				// Validate Text Field
				if (isPrimaryFieldValid && isLocalFieldValid) {

					fxUtils.setTextValidLabel(parentFlowPane, textField);

					if (localField != null) {
						fxUtils.setTextValidLabel(parentFlowPane, localField);

					}
//					fxUtils.hideErrorMessageLabel(parentFlowPane, textField);

					LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
							"add text field value to session");

					RegistrationDTO registrationDTO = getRegistrationDTOFromSession();

					if (registrationDTO != null) {
//				 Save information to session
						if (registrationDTO.getDefaultUpdatableFields() != null
								&& registrationDTO.getDefaultUpdatableFields().contains(textField.getId())) {
							addTextFieldToSession(textField.getId(), registrationDTO, true);
						}
						if (!registrationDTO.getRegistrationCategory().equals(RegistrationConstants.PACKET_TYPE_UPDATE)
								|| (registrationDTO.getRegistrationCategory()
										.equals(RegistrationConstants.PACKET_TYPE_UPDATE)
										&& registrationDTO.getUpdatableFields().contains(textField.getId()))) {
							addTextFieldToSession(textField.getId(), registrationDTO, false);
						}

						LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
								"Refresh all groups");

						// Group level visibility listeners
						refreshDemographicGroups(registrationDTO.getMVELDataContext());
					}
				} else {

					fxUtils.showErrorLabel(textField, parentFlowPane);

					if (localField != null) {
						fxUtils.showErrorLabel(localField, parentFlowPane);

					}
				}
			}
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void refreshDemographicGroups(Map<String, Object> mvelDataMap) {
		// TODO - Add loggers
		// TODO - remove demo/doc/biometrics from session, if mvel says not required
		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Refreshing demographic groups");

		Map<String, Map<String, Object>> context = new HashMap();
		context.put("identity", mvelDataMap);
		for (Screen screen : getScreens(RegistrationConstants.DEMOGRAPHIC_DETAIL)) {
			List<Group> groups = screen.getGroups();
			if (groups != null && !groups.isEmpty()) {
				for (Group group : groups) {
					boolean required = true;
					if (group.getVisible() != null
							&& RegistrationConstants.MVEL_TYPE.equalsIgnoreCase(group.getVisible().getEngine())
							&& group.getVisible().getExpr() != null && !group.getVisible().getExpr().isEmpty()) {
						RequiredOnExpr requiredOnExpr = group.getVisible();
						VariableResolverFactory resolverFactory = new MapVariableResolverFactory(context);
						required = MVEL.evalToBoolean(requiredOnExpr.getExpr(), resolverFactory);
					}
					updateFields(group.getFields(), required);
				}
			}
		}
	}

	private void updateFields(List<Field> fields, boolean isVisible) {

		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID, "Updating fields");

		for (Field field : fields) {

			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Updating field : " + field.getId());

			Node node = getFxElement(field.getId());

			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Updating visibility for field : " + field.getId() + " as visibility : " + isVisible);

			if (node != null) {
				node.getParent().getParent().getParent().setVisible(isVisible);
				node.getParent().getParent().getParent().setManaged(isVisible);
			}

			// TODO same for local lang tooo
			Node localLangNode = getFxElement(field.getId() + RegistrationConstants.LOCAL_LANGUAGE);

			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Updating visibility for field : " + field.getId() + RegistrationConstants.LOCAL_LANGUAGE
							+ " as visibility : " + isVisible);

			if (localLangNode != null) {
				localLangNode.getParent().getParent().getParent().setVisible(isVisible);
				localLangNode.getParent().getParent().getParent().setManaged(isVisible);
			}
		}
	}

	private Node getFxElement(String fieldId) {

		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Fetch fx element : " + fieldId);
		return parentFlowPane.lookup(RegistrationConstants.HASH + fieldId);
	}

	private boolean isInputTextValid(TextField textField, String id) {

		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Validating text field " + textField.getId());

		return validation.validateTextField(parentFlowPane, textField, id, true);

//		return validation.validateTextField(parentFlowPane, primaryLangTextField,
//				primaryLangTextField.getId() + "_ontype", true);
	}

	private void setSecondaryLangText(TextField primaryField, TextField secondaryField, boolean haveToTransliterate) {

		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Setting secondary language for field : " + primaryField.getId());
		if (secondaryField != null) {
			if (haveToTransliterate) {
				try {
					secondaryField.setText(transliteration.transliterate(ApplicationContext.applicationLanguage(),
							ApplicationContext.localLanguage(), primaryField.getText()));
				} catch (RuntimeException runtimeException) {
					LOGGER.error(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
							runtimeException.getMessage());

					LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
							"Exception occured while transliterating secondary language for field : "
									+ primaryField.getId());
					secondaryField.setText(primaryField.getText());
				}
			} else {

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Not transliteration into local language" + primaryField.getId());
				secondaryField.setText(primaryField.getText());
			}
		}
	}

	private Map<String, List<Field>> getTemplateGroupMap() {

		Map<String, List<Field>> templateGroupMap = new LinkedHashMap<>();

		for (Entry<String, Field> entry : getUiSchemaFieldMap().entrySet()) {
			if (isDemographicField(entry.getValue())) {

				List<Field> list = templateGroupMap.get(entry.getValue().getAlignmentGroup());
				if (list == null) {
					list = new LinkedList<Field>();
				}
				list.add(entry.getValue());
				templateGroupMap.put(entry.getValue().getAlignmentGroup() == null ? entry.getKey() + "TemplateGroup"
						: entry.getValue().getAlignmentGroup(), list);
			}
		}
		return templateGroupMap;

	}

	private void setComboBoxFieldListener(ComboBox<GenericDto> primaryComboBox,
			ComboBox<GenericDto> secondaryComboBox) {

		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Setting combo box Field Listener");

		primaryComboBox.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {

			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Setting secondary language combo box Field");
			if (secondaryComboBox != null && !secondaryComboBox.getItems().isEmpty()) {

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Setting secondary language combo box Field and items were not null");
				setSecondaryLangComboBoxVal(secondaryComboBox, primaryComboBox);
			}

			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Show Label for primary language combo box Field : " + true);
			fxUtils.toggleUIField(parentFlowPane, primaryComboBox.getId() + RegistrationConstants.LABEL, true);

			LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
					"Show Message for primary language combo box Field: " + false);
			fxUtils.toggleUIField(parentFlowPane, primaryComboBox.getId() + RegistrationConstants.MESSAGE, false);

			if (isLocalLanguageAvailable() && !isAppLangAndLocalLangSame()) {

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Show Label for secondary language combo box Field : " + true);
				fxUtils.toggleUIField(parentFlowPane, secondaryComboBox.getId() + RegistrationConstants.LABEL, true);

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Show Message for secondary language combo box Field: " + false);
				fxUtils.toggleUIField(parentFlowPane, secondaryComboBox.getId() + RegistrationConstants.MESSAGE, false);
			}

			RegistrationDTO registrationDTO = getRegistrationDTOFromSession();

			if (registrationDTO != null) {
				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Adding combox field to session ");
//		 Save information to session
				registrationDTO.addDemographicField(primaryComboBox.getId(),
						applicationContext.getApplicationLanguage(),
						primaryComboBox == null ? null
								: primaryComboBox.getValue() != null ? primaryComboBox.getValue().getName() : null,
						applicationContext.getLocalLanguage(), secondaryComboBox == null ? null
								: secondaryComboBox.getValue() != null ? secondaryComboBox.getValue().getName() : null);

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Refresh all groups");

				// Group level visibility listeners
				refreshDemographicGroups(registrationDTO.getMVELDataContext());
			}
		});

	}

	private void setSecondaryLangComboBoxVal(ComboBox<?> localComboBox, ComboBox<?> ComboBox) {

		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Setting secondary language combo box Field");
		ObservableList<?> localComboBoxValues = localComboBox.getItems();
		ObservableList<?> comboBoxValues = ComboBox.getItems();
		Object selectedOption = ComboBox.getValue();

		if (!localComboBoxValues.isEmpty() && selectedOption != null) {
			IntPredicate findIndexOfSelectedItem = null;
			if (localComboBoxValues.get(0) instanceof GenericDto && selectedOption instanceof GenericDto) {
				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Setting secondary language combo box Field of generic dto");
				findIndexOfSelectedItem = index -> ((GenericDto) localComboBoxValues.get(index)).getCode()
						.equals(((GenericDto) selectedOption).getCode());
			} else if (localComboBoxValues.get(0) instanceof String && selectedOption instanceof String) {

				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Setting secondary language combo box Field of STRING");
				findIndexOfSelectedItem = index -> ((String) comboBoxValues.get(index))
						.equals(((String) ComboBox.getSelectionModel().getSelectedItem()));
				OptionalInt indexOfSelectedLocation = IntStream.range(0, comboBoxValues.size())
						.filter(findIndexOfSelectedItem).findFirst();
				if (indexOfSelectedLocation.isPresent()) {

					LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
							"Found index of location of string value");
					localComboBox.getSelectionModel().select(indexOfSelectedLocation.getAsInt());
				}
				return;
			}
			OptionalInt indexOfSelectedLocation = IntStream.range(0, localComboBoxValues.size())
					.filter(findIndexOfSelectedItem).findFirst();

			if (indexOfSelectedLocation.isPresent()) {
				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Setting Secondary language combo box field based on index");
				localComboBox.getSelectionModel().select(indexOfSelectedLocation.getAsInt());
			}
		}
		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Setting secondary language combo box value completed");
	}

	private void initializeDemoScreens(int position) {
		LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
				"Initializing demo screens");

		demoGraphicScreenGridPaneMap.clear();

		List<Screen> screens = getScreens(RegistrationConstants.DEMOGRAPHIC_DETAIL);

		if (screens != null && !screens.isEmpty()) {

			for (Screen screen : screens) {
				LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						"Started screen : " + screen.getName());
				int screen_row_index = 0;
				int screen_column_index = 0;
				// TODO Get All Screens Group Fields

				if (screen.isVisible() && screen.getGroups() != null && !screen.getGroups().isEmpty()) {

					GridPane screenGridPane = (GridPane) getScreenNode(screen.getName(), screen.getOrder());

					screenGridPane.setVisible(false);

					screenGridPane.setManaged(false);

					addNode(screen.getName(), screen.getOrder(), screenGridPane);

					parentFlowPane.getChildren().add(screenGridPane);
					for (Group group : screen.getGroups()) {

						LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
								"Verifying group : " + group.getName());

						int group_row_index = 0;
						int group_column_index = 0;
						// TODO Find group is visible or not
						boolean isVisible = true;
						GridPane groupGridPane = new GridPane();
						groupGridPane.setId(group.getName() + group.getOrder());

						screenGridPane.add(groupGridPane, screen_column_index, screen_row_index);

						screen_row_index++;

						if (isVisible && group.getFields() != null && !group.getFields().isEmpty()) {
							LOGGER.debug(loggerClassName, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
									"Group is active : " + group.getName());

							List<Field> list = group.getFields();

							if (list.size() <= 4) {
								addGroupInUI(list, position, groupGridPane);

							} else {
								for (int index = 0; index <= list.size() / 4; index++) {

									// TODO temporary fix
									GridPane subGridPane = new GridPane();

									int toIndex = ((index * 4) + 3) <= list.size() - 1 ? ((index * 4) + 4)
											: list.size();
									List<Field> subList = list.subList(index * 4, toIndex);
									addGroupInUI(subList, position, subGridPane);

									groupGridPane.add(subGridPane, group_column_index, index);

								}
							}
						}
					}

				}
			}

//				createSceneGridPane(screen);
		}

		// TODO Need to handle in screen level
	}

}
