package com.vaadin.expensemanager.reference.ui;

import com.vaadin.expensemanager.base.ui.AdminEditor;
import com.vaadin.expensemanager.base.ui.EditorFormSpec;
import com.vaadin.expensemanager.base.ui.ReferenceConfigEditor;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.reference.VatRateDto;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

import static com.vaadin.expensemanager.reference.ui.ReferenceViewSupport.formatPercent;

/**
 * ADMIN-only settings screen for VAT rates (issue #22, ADR-0018) — one of the
 * two reference-data screens (see {@link ExpenseTypeView} for expense types).
 *
 * <p>An admin can <strong>add, edit, reorder, and deactivate</strong> rates.
 * Two-layer authorization (ADR-0008): {@code @RolesAllowed("ADMIN")} gates
 * navigation (a USER can't reach the route and the auto-menu hides its
 * {@code @Menu} entry), while the real enforcement is
 * {@link ReferenceDataService}'s method security. There is no delete — the only
 * removal is <em>deactivate</em>, which flips the {@code active} flag so the row
 * is hidden from new line choices but retained for historical lines (ADR-0018);
 * the grid shows active and inactive rows so an admin can reactivate.
 *
 * <p>The whole grid + add/edit dialog + always-enabled-Save + reorder +
 * active-toggle behaviour is the shared, generic
 * {@link ReferenceConfigEditor} — this class only supplies the VAT-rate
 * {@link ReferenceConfigEditor.Config}: its column, its single required-rate
 * field, and its service calls.
 */
@Route("vat-rates")
@PageTitle("VAT rates")
@Menu(title = "VAT rates", order = 2, icon = "vaadin:money")
@RolesAllowed("ADMIN")
public class VatRateView extends ReferenceConfigEditor<VatRateDto> {

    public VatRateView(ReferenceDataService service) {
        super(config(service));
    }

    private static ReferenceConfigEditor.Config<VatRateDto> config(ReferenceDataService service) {
        return new ReferenceConfigEditor.Config<VatRateDto>()
                .heading("VAT rates")
                .addButtonText("Add VAT rate")
                .intro("The VAT rates expense lines are filed against. Deactivating a "
                        + "rate hides it from new lines but keeps it on existing "
                        + "ones — nothing is deleted, so past reports keep their "
                        + "original rate.")
                .column("Rate", dto -> formatPercent(dto.value()), 0)
                .showStatus(true)
                .id(VatRateDto::id)
                .active(VatRateDto::active)
                .items(service::allVatRates)
                .editLabel(dto -> "Edit rate " + formatPercent(dto.value()))
                .reorder(dto -> "rate " + formatPercent(dto.value()), service::moveVatRate)
                .toggle(dto -> "rate " + formatPercent(dto.value()), service::setVatRateActive)
                .editorForm(existing -> form(service, existing));
    }

    private static EditorFormSpec<?> form(ReferenceDataService service, VatRateDto existing) {
        var model = new AdminEditor.DecimalHolder();
        var binder = new Binder<AdminEditor.DecimalHolder>();
        var field = AdminEditor.requiredDecimalField("Rate (%)", "Rate", binder,
                AdminEditor.DecimalHolder::getValue, AdminEditor.DecimalHolder::setValue);

        if (existing != null) {
            model.setValue(existing.value());
        }
        binder.readBean(model);

        return new EditorFormSpec<>(existing == null ? "Add VAT rate" : "Edit VAT rate",
                field, binder, model, () -> {
                    if (existing == null) {
                        service.createVatRate(model.getValue());
                    } else {
                        service.updateVatRate(existing.id(), model.getValue());
                    }
                });
    }
}
