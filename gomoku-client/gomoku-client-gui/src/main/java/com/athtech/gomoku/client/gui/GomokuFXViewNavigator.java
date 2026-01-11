package com.athtech.gomoku.client.gui;
//STUDENTS-CODE-NUMBER : CSY-22117
import com.athtech.gomoku.client.gui.controllers.BaseController;
import com.athtech.gomoku.client.gui.enums.View;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;

public class GomokuFXViewNavigator {

    private final Map<View, Parent> roots = new EnumMap<>(View.class); // initialize the map that will store all fxml elements
    private final Map<View, BaseController> controllers = new EnumMap<>(View.class); // initialize the map that will store all controller references
    private View currentView; // store the path of current fxml element
    private StackPane contentPane; // used as an anchor to swap the roots inside of

    // Passes references to the root we want to preload to the loader and initializes the relevant controller (load FXMLs)
    public void preload(View view) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(view.fxmlPath()));

            Parent root = loader.load();
            BaseController controller = loader.getController();

            roots.put(view, root);
            controllers.put(view, controller);

        } catch (Exception e) {
            throw new RuntimeException("Failed to preload " + view, e);
        }
    }

    // We use Base Controller init() method (shared by all controllers)
    // Pass references to navigator, networkHandler, shared data
    public void initControllers(
            GomokuFXViewNavigator navigator,
            GomokuFXNetworkHandler networkHandler,
            GomokuFXCommonToAllControllersData data
    ) {
        controllers.values().forEach(
                c -> c.init(navigator, networkHandler, data)
        );
    }

    // Swap the roots within ContentPane
    public void goTo(View view) {
        if(view == View.SCENEWRAPPER) return;//safety from inception...
        Parent root = roots.get(view);

        if (root == null) {
            System.err.println("Cannot go to view " + view + " — it has not been preloaded.");
            return;
        }

        if (roots.get(view) == null){
            System.err.println("Cannot go to view " + view + " — it has empty fxml path...");
            return;
        }

        if (currentView != null) {
            controllers.get(currentView).onLeave();
        }

//        stage.getScene().setRoot(roots.get(view));
        // Swap inside the wrapper container
        contentPane.getChildren().setAll(root);

        currentView = view;
        controllers.get(view).onEnter();
    }


//      ---- GETTERS & SETTERS ----      //

    public BaseController getController(View view) {
        return controllers.get(view);
    }

    public Parent getRoot(View view){
        return roots.get(view);
    }

    public Parent getWrapper(){
        return roots.get(View.SCENEWRAPPER);
    }

    public StackPane getContentPane() {
        return contentPane;
    }

    public void setTheContentPane() {
        this.contentPane = (StackPane) getRoot(View.SCENEWRAPPER).lookup("#contentPane");
    }


}
