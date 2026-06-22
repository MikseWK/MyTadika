import { useState } from 'react'
import { useAllergies, useUpdateAllergies } from '../../hooks/useHealthRecords'

const COMMON_ALLERGENS = ['milk', 'peanut']

function AllergyAlertBanner({ studentId, canEdit }) {
  const { data: allergies, isLoading, isError } = useAllergies(studentId)
  const updateAllergies = useUpdateAllergies(studentId)
  const [editing, setEditing] = useState(false)
  const [selected, setSelected] = useState([])
  const [customInput, setCustomInput] = useState('')

  function startEditing() {
    setSelected(allergies ?? [])
    setEditing(true)
  }

  function toggleAllergen(allergen) {
    setSelected((prev) =>
      prev.includes(allergen) ? prev.filter((a) => a !== allergen) : [...prev, allergen],
    )
  }

  function addCustom() {
    const value = customInput.trim().toLowerCase()
    if (value && !selected.includes(value)) {
      setSelected((prev) => [...prev, value])
    }
    setCustomInput('')
  }

  async function handleSave() {
    await updateAllergies.mutateAsync(selected)
    setEditing(false)
  }

  if (isLoading) return null

  if (isError) {
    return (
      <div className="allergy-banner">
        <strong>Allergies:</strong> Could not load allergy information. Please refresh the page.
      </div>
    )
  }

  const extraAllergens = selected.filter((a) => !COMMON_ALLERGENS.includes(a))

  return (
    <div className={`allergy-banner ${allergies?.length ? 'allergy-banner-active' : ''}`}>
      {!editing ? (
        <div className="allergy-banner-row">
          <div>
            <strong>Allergies:</strong> {allergies?.length ? allergies.join(', ') : 'None on file'}
          </div>
          {canEdit && (
            <button type="button" className="btn btn-secondary" onClick={startEditing}>
              Edit
            </button>
          )}
        </div>
      ) : (
        <div className="allergy-banner-edit">
          <div className="allergy-chip-row">
            {COMMON_ALLERGENS.map((allergen) => (
              <label key={allergen} className="allergy-chip">
                <input
                  type="checkbox"
                  checked={selected.includes(allergen)}
                  onChange={() => toggleAllergen(allergen)}
                />
                {allergen}
              </label>
            ))}
            {extraAllergens.map((allergen) => (
              <label key={allergen} className="allergy-chip">
                <input type="checkbox" checked onChange={() => toggleAllergen(allergen)} />
                {allergen}
              </label>
            ))}
          </div>
          <div className="allergy-add-row">
            <input
              className="text-input"
              placeholder="Add another allergen…"
              value={customInput}
              onChange={(event) => setCustomInput(event.target.value)}
            />
            <button type="button" className="btn btn-secondary" onClick={addCustom}>
              Add
            </button>
          </div>
          <div className="form-actions">
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleSave}
              disabled={updateAllergies.isPending}
            >
              {updateAllergies.isPending ? 'Saving…' : 'Save'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => setEditing(false)}>
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export default AllergyAlertBanner
