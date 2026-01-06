package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.client.gui.controllers.BaseController;
import com.athtech.gomoku.client.gui.enums.View;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.EnumMap;
import java.util.Map;

public class GomokuFXViewNavigator {

    private final Map<View, Parent> roots = new EnumMap<>(View.class);
    private final Map<View, BaseController> controllers = new EnumMap<>(View.class);
    private View currentView;
    private StackPane contentPane;

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

    public void initControllers(
            GomokuFXViewNavigator navigator,
            GomokuFXNetworkHandler networkHandler,
            GomokuFXCommonToAllControllersData data
    ) {
        controllers.values().forEach(
                c -> c.init(navigator, networkHandler, data)
        );
    }

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
