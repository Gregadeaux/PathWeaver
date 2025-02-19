package edu.wpi.first.pathweaver.path.wpilib;

import edu.wpi.first.pathweaver.DataFormats;
import edu.wpi.first.pathweaver.FxUtils;
import edu.wpi.first.pathweaver.Waypoint;
import edu.wpi.first.pathweaver.global.CurrentSelections;
import edu.wpi.first.pathweaver.path.Path;
import edu.wpi.first.pathweaver.path.PathUtil;
import edu.wpi.first.pathweaver.spline.wpilib.WpilibSpline;
import javafx.collections.ListChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.TransferMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WpilibPath extends Path {
    private final Group iconGroup = new Group();
    private final Group tangentGroup = new Group();
    private final Group outlineGroup = new Group();

    /**
     * Path constructor based on a known list of points.
     *
     * @param points        The list of waypoints to add
     * @param name          The name of the path
     */

    public WpilibPath(List<Waypoint> points, String name) {
        super(WpilibSpline::new, name);
        this.waypoints.addListener((ListChangeListener<Waypoint>) c -> {
            Waypoint first = this.waypoints.get(0);
            while (c.next()) {
                for (Waypoint wp : c.getAddedSubList()) {
                    setupWaypoint(wp);
                    iconGroup.getChildren().add(wp.getIcon());
                    tangentGroup.getChildren().add(wp.getTangentLine());
                    outlineGroup.getChildren().add(wp.getRobotOutline());
                    if (wp != first) {
                        wp.reversedProperty().bindBidirectional(first.reversedProperty());
                    }
                    wp.reversedProperty().addListener(l -> {
                        wp.reverseTangent();
                        update();
                    });
                }

                for (Waypoint wp : c.getRemoved()) {
                    iconGroup.getChildren().remove(wp.getIcon());
                    tangentGroup.getChildren().remove(wp.getTangentLine());
                    outlineGroup.getChildren().remove(wp.getRobotOutline());
                }
            }
            update();
        });
        this.spline.addToGroup(this.mainGroup, DEFAULT_SPLINE_SCALE / field.getScale());
        this.mainGroup.getChildren().addAll(this.iconGroup, this.tangentGroup, this.outlineGroup);
        this.waypoints.addAll(points);

        update();
        enableSubchildSelector(subchildIdx);
    }

    private void setupDrag(Waypoint waypoint) {
        waypoint.getIcon().setOnDragDetected(event -> {
            CurrentSelections.setCurWaypoint(waypoint);
            CurrentSelections.setCurPath(this);
            waypoint.getIcon().startDragAndDrop(TransferMode.MOVE)
                    .setContent(Map.of(DataFormats.WAYPOINT, "point"));
        });

        waypoint.getTangentLine().setOnDragDetected(event -> {
            CurrentSelections.setCurWaypoint(waypoint);
            CurrentSelections.setCurPath(this);
            waypoint.getTangentLine().startDragAndDrop(TransferMode.MOVE)
                    .setContent(Map.of(DataFormats.CONTROL_VECTOR, "vector"));
        });
    }

    private void setupClick(Waypoint waypoint) {
        waypoint.getIcon().setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                toggleWaypoint(waypoint);
            } else if (e.getClickCount() == 2) {
                waypoint.setLockTangent(false);
            }
            e.consume();
        });

        waypoint.getTangentLine().setOnMouseClicked(e -> {
            toggleWaypoint(waypoint);
            if (e.getClickCount() == 2) {
                waypoint.setLockTangent(false);
                e.consume();
            }
        });
    }

    /**
     * This implementation calls {@link PathUtil#rawThetaOptimization(Point2D, Point2D, Point2D)} to update the tangent line.
     * 
     * @param wp the waypoint to update the tangent line for.
     */
    @Override
    protected void updateTangent(Waypoint wp) {
        int curWpIndex = getWaypoints().indexOf(wp);
        if (curWpIndex - 1 < 0 || curWpIndex + 1 >= waypoints.size() || wp.isLockTangent()) {
            return;
        }

        Waypoint previous = getWaypoints().get(curWpIndex - 1);
        Waypoint next = getWaypoints().get(curWpIndex + 1);

        Point2D wpTangent = PathUtil.rawThetaOptimization(previous.getCoords(), wp.getCoords(), next.getCoords());
        if (wp.isReversed()) {
            wpTangent = wpTangent.multiply(-1);
        }
        wp.setTangent(wpTangent);
    }

    private void setupWaypoint(Waypoint waypoint) {
        setupDrag(waypoint);
        setupClick(waypoint);

        waypoint.getIcon().setOnContextMenuRequested(e -> {
            ContextMenu menu = new ContextMenu();
            if (getWaypoints().size() > 2) {
                menu.getItems().add(FxUtils.menuItem("Delete", event -> removeWaypoint(waypoint)));
            }
            if (waypoint.getTangentLine().isVisible()) {
                menu.getItems().add(FxUtils.menuItem("Hide control vector",
                        event -> waypoint.getTangentLine().setVisible(false)));
            } else {
                menu.getItems().add(FxUtils.menuItem("Show control vector",
                        event -> waypoint.getTangentLine().setVisible(true)));
            }
            menu.getItems().add(FxUtils.menuItem("Reverse Vector",
                    event -> waypoint.setReversed(!waypoint.isReversed())));
            menu.show(mainGroup.getScene().getWindow(), e.getScreenX(), e.getScreenY());
        });

        waypoint.getIcon().setScaleX(DEFAULT_CIRCLE_SCALE / field.getScale());
        waypoint.getIcon().setScaleY(DEFAULT_CIRCLE_SCALE / field.getScale());
        waypoint.getTangentLine().setStrokeWidth(DEFAULT_LINE_SCALE / field.getScale());
        waypoint.getRobotOutline().setStrokeWidth(DEFAULT_LINE_SCALE / field.getScale());
    }

    /**
     * Path constructor based on default start and end points.
     *
     * @param name          The name of the path
     */
    public WpilibPath(String name) {
        this(new Point2D(0, 0), new Point2D(10, -10), new Point2D(10, 0),
                new Point2D(0, -10), name);
    }

    /**
     * Path constructor based on known start and end points.
     *
     * @param startPos      The starting waypoint of new path
     * @param endPos        The ending waypoint of new path
     * @param startTangent  The starting tangent vector of new path
     * @param endTangent    The ending tangent vector of new path
     * @param name          The string name to assign path, also used for naming exported files
     */
    private WpilibPath(Point2D startPos, Point2D endPos, Point2D startTangent, Point2D endTangent, String name) {
        this(List.of(new Waypoint(startPos, startTangent, true, false), new Waypoint(endPos, endTangent, true, false)), name);
    }

    @Override
    public String toString() {
        return getPathName();
    }

    /**
     * Returns all the tangent lines for the waypoints.
     *
     * @return Collection of Tangent Lines.
     */
    public Collection<Node> getTangentLines() {
        return getWaypoints().stream().map(Waypoint::getTangentLine).collect(Collectors.toList());
    }

    /**
     * Duplicates a path.
     *
     * @param newName filename of the new path.
     * @return the new path.
     */
    @Override
    public Path duplicate(String newName) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (Waypoint wp : getWaypoints()) {
            waypoints.add(wp.copy());
        }
        return new WpilibPath(waypoints, newName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        WpilibPath path = (WpilibPath) o;
        if (!pathName.equals(path.pathName)) {
            return false;
        }

        return getWaypoints().equals(path.getWaypoints());
    }
}
